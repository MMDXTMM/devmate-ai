package benchmark.sql;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrderQueryService {
    private final UserRepository userRepository;

    public OrderQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Map<Long, String> loadUsers(List<Long> userIds) {
        Map<Long, String> result = new LinkedHashMap<>();
        for (Long userId : userIds) {
            result.put(userId, userRepository.findNameById(userId));
        }
        return result;
    }

    public interface UserRepository {
        String findNameById(long userId);
    }
}
