package kz.javastart.deposit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.javastart.deposit.controller.dto.DepositResponseDTO;
import kz.javastart.deposit.entity.Deposit;
import kz.javastart.deposit.exception.DepositServiceException;
import kz.javastart.deposit.repository.DepositRepository;
import kz.javastart.deposit.rest.AccountServiceClient;
import kz.javastart.deposit.rest.BillServiceClient;
import kz.javastart.deposit.rest.dto.AccountResponseDTO;
import kz.javastart.deposit.rest.dto.BillRequestDTO;
import kz.javastart.deposit.rest.dto.BillResponseDTO;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.metrics.export.newrelic.NewRelicProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
public class DepositService {
    private static final String TOPIC_EXCHANGE_DEPOSIT = "deposit";
    private static final String ROUTING_KEY_DEPOSIT = "deposit";

    private final DepositRepository depositRepository;

    private final AccountServiceClient accountServiceClient;

    private final BillServiceClient billServiceClient;

    private final RabbitTemplate rabbitTemplate;

    @Autowired
    public DepositService(DepositRepository depositRepository, AccountServiceClient accountServiceClient, BillServiceClient billServiceClient, RabbitTemplate rabbitTemplate) {
        this.depositRepository = depositRepository;
        this.accountServiceClient = accountServiceClient;
        this.billServiceClient = billServiceClient;
        this.rabbitTemplate = rabbitTemplate;
    }

    public DepositResponseDTO deposit(Long accountId, Long billId, BigDecimal amount) {
        if (accountId == null && billId == null) {
            throw new DepositServiceException("Account id is null and bill id is null");
        }

        BillResponseDTO billResponseDTO =  getBillResponseDto(accountId, billId);
        BillRequestDTO billRequestDTO = createBillRequest(amount, billResponseDTO);
        billId = billResponseDTO.getBillId();

        billServiceClient.update(billId, billRequestDTO);

        AccountResponseDTO accountResponseDTO = accountServiceClient.getAccountById(billResponseDTO.getAccountId());
        depositRepository.save(new Deposit(amount, billResponseDTO.getBillId(), OffsetDateTime.now(), accountResponseDTO.getEmail()));

        DepositResponseDTO depositResponseDTO = new DepositResponseDTO(amount, accountResponseDTO.getEmail());

        setToQueue(depositResponseDTO);

        return  depositResponseDTO;
    }

    private BillResponseDTO getBillResponseDto(Long accountId, Long billId) {
        if (billId != null) {
            return billServiceClient.getBillById(billId);
        } else {
            return getDefaultBill(accountId);
        }
    }

    private void setToQueue(DepositResponseDTO depositResponseDTO) {
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            rabbitTemplate.convertAndSend(TOPIC_EXCHANGE_DEPOSIT, ROUTING_KEY_DEPOSIT,
                    objectMapper.writeValueAsString(depositResponseDTO));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new DepositServiceException("Can't send message to RabbitMQ");
        }
    }

    private BillRequestDTO createBillRequest(BigDecimal amount, BillResponseDTO billResponseDTO) {
        BillRequestDTO billRequestDTO = new BillRequestDTO();
        billRequestDTO.setAccountId(billResponseDTO.getAccountId());
        billRequestDTO.setCreationDate(billResponseDTO.getCreationDate());
        billRequestDTO.setIsDefault(billResponseDTO.getIsDefault());
        billRequestDTO.setOverdraftEnabled(billResponseDTO.getOverdraftEnabled());
        billRequestDTO.setAmount(billResponseDTO.getAmount().add(amount));

        return billRequestDTO;
    }

    private BillResponseDTO getDefaultBill(Long accountId) {
        return billServiceClient.getBillsByAccountId(accountId).stream()
                .filter(BillResponseDTO::getIsDefault)
                .findAny()
                .orElseThrow(() -> new DepositServiceException("Unable to find default bill for account: " + accountId));
    }
}
