package benchmark.cache;

public class ProductService {
    private final ProductCache cache;
    private final ProductRepository repository;

    public ProductService(ProductCache cache, ProductRepository repository) {
        this.cache = cache;
        this.repository = repository;
    }

    public String findProduct(long id) {
        String cached = cache.get(id);
        if (cached != null) {
            return cached;
        }
        String product = repository.findById(id);
        cache.put(id, product);
        return product;
    }

    public interface ProductCache {
        String get(long id);
        void put(long id, String value);
    }

    public interface ProductRepository {
        String findById(long id);
    }
}
