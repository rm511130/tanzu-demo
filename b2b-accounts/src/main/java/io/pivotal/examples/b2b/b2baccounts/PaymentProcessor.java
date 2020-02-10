package io.pivotal.examples.b2b.b2baccounts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class PaymentProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentProcessor.class);

    private final AccountRepository accountRepository;
    private final RestTemplate restTemplate;

    @Value("${payments.host:localhost:8080}")
    private String paymentsHost;

    public PaymentProcessor(AccountRepository accountRepository, RestTemplate restTemplate) {
        this.accountRepository = accountRepository;
        this.restTemplate = restTemplate;
    }

    @Transactional
    @StreamListener(PaymentChannels.CONFIRMATIONS)
    public void confirmation(PaymentConfirmation confirmation) {
        LOGGER.info("Payment Confirmation [{}]", confirmation.getPaymentId());

        Payment payment = this.restTemplate.getForObject("http://" + paymentsHost + "/{paymentId}", Payment.class, confirmation.getPaymentId());

        if (payment != null) {
            LOGGER.info("Processing Payment [{}] with amount [{}]", payment.getPaymentId(), payment.getAmount());
            Optional<Account> origin = this.accountRepository.findById(payment.getOriginAccount());
            Optional<Account> destination = this.accountRepository.findById(payment.getDestinationAccount());

            Account originAccount = origin.orElse(new Account(payment.getOriginAccount(), BigDecimal.ZERO));
            Account destinationAccount = destination.orElse(new Account(payment.getDestinationAccount(), BigDecimal.ZERO));

            BigDecimal credit = new BigDecimal(payment.getAmount().replace('€', '0'));
            BigDecimal debit = credit.multiply(new BigDecimal(-1));

            destinationAccount.setBalance(destinationAccount.getBalance().add(credit));
            originAccount.setBalance(originAccount.getBalance().add(debit));

            accountRepository.save(originAccount);
            accountRepository.save(destinationAccount);
        }
    }
}
