package benchmark.message;

public class PaymentConsumer {
    private final PaymentRepository paymentRepository;

    public PaymentConsumer(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public void consume(PaymentMessage message, Acknowledgement acknowledgement) {
        acknowledgement.ack();
        paymentRepository.save(message);
    }

    public interface PaymentRepository {
        void save(PaymentMessage message);
    }

    public interface Acknowledgement {
        void ack();
    }

    public record PaymentMessage(String paymentId) {
    }
}
