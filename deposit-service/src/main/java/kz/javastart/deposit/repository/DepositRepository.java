package kz.javastart.deposit.repository;

import kz.javastart.deposit.entity.Deposit;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface DepositRepository extends CrudRepository<Deposit, Long> {
    List<Deposit> findDepositByEmail(String email);
}
