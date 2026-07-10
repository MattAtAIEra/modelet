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
| `OpenSpirit/` | Java | The original framework (2006–), built on Spring JDBC. Produces `modelet-2.1.x.jar` via `ant core-jar`. |
| `python/` | Python 3.10+ | `pymodelet`, a faithful port of the same philosophy on DB-API 2.0 with dataclass entities. Zero runtime dependencies. |

The two trees are independent — each has its own build and tests — but they
share the same concepts and naming (Entity, TxnMode, Model, PagingElement,
PageContainer, AppEntity audit fields), so knowledge transfers 1:1 between
them.

## Quick taste

Java:

```java
Book book = new Book();
book.setBookName("Java note book");
model.save(book);                                  // INSERT, id written back

List<Book> books = model.find(
    "select * from book where id > ?", new Object[]{5L}, Book.class);

book.setTxnMode(TxnMode.DELETE);
model.save(book);
```

Python:

```python
book = Book(book_name="Python note book")
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
