package benchmark.concurrency;

public class InventoryService {
    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public boolean reserve(long productId, int quantity) {
        int stock = inventoryRepository.findStock(productId);
        if (stock < quantity) {
            return false;
        }
        inventoryRepository.updateStock(productId, stock - quantity);
        return true;
    }

    public interface InventoryRepository {
        int findStock(long productId);
        void updateStock(long productId, int stock);
    }
}
