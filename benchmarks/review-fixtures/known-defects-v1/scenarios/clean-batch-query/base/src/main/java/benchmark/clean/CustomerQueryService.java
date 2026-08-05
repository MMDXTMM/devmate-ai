package benchmark.clean;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CustomerQueryService {
    private final CustomerRepository repository;

    public CustomerQueryService(CustomerRepository repository) {
        this.repository = repository;
    }

    public Map<Long, String> loadNames(List<Long> ids) {
        Map<Long, String> result = new LinkedHashMap<>();
        for (Long id : ids) {
            result.put(id, repository.findNameById(id));
        }
        return result;
    }

    public interface CustomerRepository {
        String findNameById(long id);
    }
}
