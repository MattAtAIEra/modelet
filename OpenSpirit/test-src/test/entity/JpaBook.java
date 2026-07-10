package test.entity;

import java.math.BigDecimal;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import modelet.entity.AbstractEntity;
import test.AEnum;

/**
 * Same book table as {@link Book}, declared with Jakarta Persistence annotation
 * vocabulary instead of the convention methods. getTableName() deliberately returns
 * a wrong name to prove that @Table wins over the convention.
 */
@Table(name = "book")
public class JpaBook extends AbstractEntity {

  @Column(name = "bookName")
  private String title;

  private BigDecimal price;

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "gradeOrdinal")
  private AEnum grade;

  private Date createDate;

  @Transient
  private Integer joinedRowCount;

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public AEnum getGrade() {
    return grade;
  }

  public void setGrade(AEnum grade) {
    this.grade = grade;
  }

  public Date getCreateDate() {
    return createDate;
  }

  public void setCreateDate(Date createDate) {
    this.createDate = createDate;
  }

  public Integer getJoinedRowCount() {
    return joinedRowCount;
  }

  public void setJoinedRowCount(Integer joinedRowCount) {
    this.joinedRowCount = joinedRowCount;
  }

  public String getTableName() {
    return "WRONG_TABLE_ANNOTATION_SHOULD_WIN";
  }
}
