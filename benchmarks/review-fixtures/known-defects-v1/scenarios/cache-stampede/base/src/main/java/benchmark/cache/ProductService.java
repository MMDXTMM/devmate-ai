package benchmark.cache;

public class ProductService {
    private final ProductCache cache;
    private final ProductRepository repository;

    public ProductService(ProductCache cache, ProductRepository repository) {
        this.cache = cache;
        this.repository = repository;
    }

    public String findProduct(long id) {
        return cache.getOrLoad(id, () -> repository.findById(id));
    }

    public interface ProductCache {
        String getOrLoad(long id, Loader loader);
    }

    public interface Loader {
        String load();
    }

    public interface ProductRepository {
        String findById(long id);
    }
}
