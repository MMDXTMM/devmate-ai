package com.devmate.generation.service;
import com.devmate.generation.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class DockerGenerationSandboxTest {
    final DockerGenerationSandbox runner=new DockerGenerationSandbox(new ObjectMapper(),false,"colima-ragent");
    static ProjectBundle bundle(String path,String code) { return new ProjectBundle("test","test","run",List.of(new ProjectBundle.ProjectFile(path,code),new ProjectBundle.ProjectFile("README.md","Test")),List.of()); }
    @Test void blocksMissingTestsExternalDependenciesAndUnsupportedStacks() {
        assertThat(runner.verify(bundle("main.py","print('hi')")).status()).isEqualTo("BLOCKED");
        assertThat(runner.verify(bundle("src/App.vue","<template>Hi</template>")).profile()).isEqualTo("UNSUPPORTED");
        var b=new ProjectBundle("x","x","x",List.of(new ProjectBundle.ProjectFile("main.py","pass"),new ProjectBundle.ProjectFile("requirements.txt","requests==2.32.3")),List.of());
        assertThat(runner.plan(b).blocked()).contains("依赖");
        assertThat(runner.plan(bundle("math.test.js","test")).tests()).isTrue();
        assertThat(runner.plan(bundle("Main.java","public class Main {} ")).tests()).isFalse();
        assertThat(runner.verify(bundle("tests/test_x.py","import unittest")).output()).contains("尚未启用");
    }
    @Test void preventsRepairFromRemovingTestsOrChangingDependencies() {
        var b=bundle("tests/test_math.py","assert 1==2");
        assertThatThrownBy(()->GenerationVerificationService.preserveChecks(b,bundle("tests/test_math.py","pass"))).hasMessageContaining("不得修改");
        assertThatThrownBy(()->GenerationVerificationService.preserveChecks(b,bundle("main.py","pass"))).hasMessageContaining("不得删除");
    }
    @Test @EnabledIfEnvironmentVariable(named="DEVMATE_SANDBOX_IT",matches="true")
    void executesPythonTestsWithoutNetworkAndReportsRealFailureAndNoTests() {
        var live=new DockerGenerationSandbox(new ObjectMapper(),true,"colima-ragent");
        var good=bundle("tests/test_isolation.py","import unittest, socket, pathlib\nclass Isolation(unittest.TestCase):\n def test_isolated(self):\n  self.assertFalse(pathlib.Path('/Users').exists())\n  self.assertEqual(socket.socket().connect_ex(('1.1.1.1',443)),101)\n");
        var pass=live.verify(good); assertThat(pass.status()).as(pass.output()).isEqualTo("PASSED");assertThat(pass.testsExecuted()).isTrue();
        var fail=live.verify(bundle("tests/test_bad.py","import unittest\nclass Bad(unittest.TestCase):\n def test_bad(self): self.assertEqual(1,2)\n"));
        assertThat(fail.status()).isEqualTo("FAILED");assertThat(fail.exitCode()).isNotZero();
        assertThat(live.verify(bundle("tests/test_empty.py","# no tests")).status()).isEqualTo("BLOCKED");
    }
    @Test @EnabledIfEnvironmentVariable(named="DEVMATE_SANDBOX_IT",matches="true")
    void executesNodeTestsAndJavaCompilationInFixedContainers() {
        var live=new DockerGenerationSandbox(new ObjectMapper(),true,"colima-ragent");
        var node=live.verify(bundle("math.test.js","const test=require('node:test'); const assert=require('node:assert/strict'); test('sum',()=>assert.equal(1+1,2));"));
        assertThat(node.status()).as(node.output()).isEqualTo("PASSED");
        var java=live.verify(bundle("Main.java","public class Main { public static void main(String[] a) { System.out.println(1); } }"));
        assertThat(java.status()).as(java.output()).isEqualTo("PASSED");assertThat(java.testsExecuted()).isFalse();
        var broken=live.verify(bundle("Main.java","public class Main { broken }"));assertThat(broken.status()).as(broken.output()).isEqualTo("FAILED");
    }
}
