package benchmark.performance;

public class PricingService {
    private final PricingClient pricingClient;

    public PricingService(PricingClient pricingClient) {
        this.pricingClient = pricingClient;
    }

    public synchronized Price refresh(long productId) {
        Price price = pricingClient.fetch(productId);
        return updateSnapshot(price);
    }

    private Price updateSnapshot(Price price) {
        return price;
    }

    public interface PricingClient {
        Price fetch(long productId);
    }

    public record Price(long productId, long cents) {
    }
}
