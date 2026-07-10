package test.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import modelet.entity.AbstractEntity;

/**
 * Entity keyed by a natural key: @Id on a custom field with no @GeneratedValue means the
 * application supplies the key itself (the annotation-era SystemIncrementEntity).
 */
@Table(name = "account")
public class JpaAccount extends AbstractEntity {

  @Id
  @Column(name = "account_no")
  private String accountNo;

  private String owner;

  public String getAccountNo() {
    return accountNo;
  }

  public void setAccountNo(String accountNo) {
    this.accountNo = accountNo;
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }

  public String getTableName() {
    return "account";
  }
}
