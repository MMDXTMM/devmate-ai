package com.devmate.review.service;

import com.devmate.review.entity.ReviewWorkflowRun;

record ReviewWorkflowStart(ReviewWorkflowRun run, boolean created) {
}
