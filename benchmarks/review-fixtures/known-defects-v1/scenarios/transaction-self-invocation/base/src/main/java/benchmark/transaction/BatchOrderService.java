package benchmark.transaction;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class BatchOrderService {
    private final OrderWriter orderWriter;

    public BatchOrderService(OrderWriter orderWriter) {
        this.orderWriter = orderWriter;
    }

    public void importOrders(Iterable<String> orders) {
        orders.forEach(orderWriter::createOrder);
    }

    public interface OrderWriter {
        void createOrder(String order);
    }
}
