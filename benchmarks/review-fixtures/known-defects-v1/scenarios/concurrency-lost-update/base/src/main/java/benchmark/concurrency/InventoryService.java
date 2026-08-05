package benchmark.concurrency;

public class InventoryService {
    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public boolean reserve(long productId, int quantity) {
        return inventoryRepository.decreaseIfEnough(productId, quantity) == 1;
    }

    public interface InventoryRepository {
        int decreaseIfEnough(long productId, int quantity);
    }
}
