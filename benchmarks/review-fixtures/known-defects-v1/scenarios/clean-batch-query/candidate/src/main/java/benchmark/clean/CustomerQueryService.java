package benchmark.clean;

import java.util.List;
import java.util.Map;

public class CustomerQueryService {
    private final CustomerRepository repository;

    public CustomerQueryService(CustomerRepository repository) {
        this.repository = repository;
    }

    public Map<Long, String> loadNames(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(repository.findNamesByIds(ids));
    }

    public interface CustomerRepository {
        Map<Long, String> findNamesByIds(List<Long> ids);
    }
}
