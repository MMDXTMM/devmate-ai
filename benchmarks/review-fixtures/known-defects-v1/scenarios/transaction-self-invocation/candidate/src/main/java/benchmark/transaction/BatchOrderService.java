package benchmark.transaction;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class BatchOrderService {
    public void importOrders(Iterable<String> orders) {
        for (String order : orders) {
            createOrder(order);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createOrder(String order) {
        persist(order);
    }

    private void persist(String order) {
    }
}
