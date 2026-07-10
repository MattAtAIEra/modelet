# Modelet

SQL-first micro ORM, born in 2006. Queries are written in the plain ANSI SQL
you already know (subqueries, joins, anything); the framework only takes over
the mechanical INSERT / UPDATE / DELETE work, driven by each entity's
transaction-mode state machine.

查詢交給 SQL,寫入交給框架。工程師只要會 ANSI SQL 就能上手,繁瑣的
INSERT / UPDATE / DELETE 由框架代勞。

## Repository layout

| Path | Language | Description |
|------|----------|-------------|
| `OpenSpirit/` | Java 17+ | The original framework (2006–), built on Spring JDBC. Produces `modelet-3.0.x.jar` via `ant core-jar`. |
| `python/` | Python 3.10+ | `pymodelet`, a faithful port of the same philosophy on DB-API 2.0 with dataclass entities. Zero runtime dependencies. |

The two trees are independent — each has its own build and tests — but they
share the same concepts and naming (Entity, TxnMode, Model, PagingElement,
PageContainer, AppEntity audit fields), so knowledge transfers 1:1 between
them.

## Jakarta Persistence vocabulary

Since Modelet 3.0, entities can be declared with the Jakarta Persistence
annotation vocabulary — `@Table`, `@Id`, `@Column`, `@Transient`,
`@Enumerated`, `@GeneratedValue` — in **both** languages. Only the vocabulary
is borrowed: the engine stays SQL-first (no JPQL, no EntityManager, no
proxies, no session cache), and every annotation falls back to the original
convention methods, so pre-2.2 entities keep working unchanged.

Java:

```java
@Table(name = "book")
public class Book extends AbstractEntity {

  @Column(name = "bookName")
  private String title;

  @Enumerated(EnumType.ORDINAL)
  private Grade grade;

  @Transient
  private Integer joinedRowCount;
  // getters/setters ...
}
```

Python:

```python
@Table("book")
@dataclass
class Book(Entity):
    title: str | None = Column("bookName")
    grade: Grade | None = Enumerated(EnumType.ORDINAL)
    joined_row_count: int | None = Transient()
```

Usage is identical in both:

```java
Book book = new Book();
book.setTitle("Java note book");
model.save(book);                                  // INSERT, id written back

List<Book> books = model.find(
    "select * from book where id > ?", new Object[]{5L}, Book.class);

book.setTxnMode(TxnMode.DELETE);
model.save(book);
```

```python
book = Book(title="Python note book")
model.save(book)                                   # INSERT, id written back

books = model.find("select * from book where id > ?", [5], Book)

book.txn_mode = TxnMode.DELETE
model.save(book)
```

## Testing

- Java: unit tests in `OpenSpirit/test-src` (JUnit 4; most cases need a
  configured database, see `OpenSpirit/config/spring/core.properties`).
- Python: `cd python && PYTHONPATH=src python3 -m pytest tests` — runs
  entirely on in-memory SQLite, no setup needed.
