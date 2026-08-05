package benchmark.sql;

import java.util.List;
import java.util.Map;

public class OrderQueryService {
    private final UserRepository userRepository;

    public OrderQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Map<Long, String> loadUsers(List<Long> userIds) {
        return userRepository.findNamesByIds(userIds);
    }

    public interface UserRepository {
        Map<Long, String> findNamesByIds(List<Long> userIds);
    }
}
