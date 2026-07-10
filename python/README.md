# pymodelet

Python port of [Modelet](../README.md) — the SQL-first micro ORM from 2006.

The founding idea, unchanged: **queries belong to SQL, writes belong to the
framework.** Anyone with ANSI SQL experience (and a subquery or two) can build
and maintain an application without learning a query DSL. The ORM's job is
only the tedious part: turning entities into INSERT / UPDATE / DELETE
statements, writing generated keys back, stamping audit fields.

- Python 3.10+, zero runtime dependencies — sits directly on any
  [DB-API 2.0](https://peps.python.org/pep-0249/) connection
  (sqlite3, PyMySQL, psycopg, ...).
- Entities are plain dataclasses.
- ~600 lines of source. Read it in one sitting.

## Usage

```python
import sqlite3
from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal

from modelet import Entity, Model, PagingElement, SqliteDialect, TxnMode

@dataclass
class Book(Entity):
    book_name: str | None = None
    price: Decimal | None = None
    create_date: datetime | None = None

    table_name = "book"

model = Model(sqlite3.connect("app.db"), SqliteDialect())

# --- writes: the framework's job -------------------------------------
book = Book(book_name="ANSI SQL in a nutshell", price=Decimal("150"))
model.save(book)          # INSERT; db-generated id written back to book.id
                          # book.txn_mode is now UPDATE

book.price = Decimal("120")
model.save(book)          # UPDATE ... WHERE id=?

book.txn_mode = TxnMode.DELETE
model.save(book)          # DELETE ... WHERE id=?

# --- queries: your job, in plain SQL ----------------------------------
rows  = model.find("select * from book where price > ?", [100])         # dicts
books = model.find("select * from book where price > ?", [100], Book)   # entities

page = model.find_with_paging(
    "select * from book order by id", None,
    PagingElement(target_page=2, rows_per_page=20), Book)
print(page.total_pages, page.total_records, page.rows)

# --- transactions ------------------------------------------------------
with model.txn():
    model.save(books_a)
    model.save(books_b)   # both commit together, roll back together
```

### Audit fields

Subclass `AppEntity` instead of `Entity` and bind a login to the current
context; `creator/create_date` are stamped on insert, `modifier/modify_date`
on update:

```python
from modelet import AppEntity, Login, set_login

set_login(Login("matt"))
```

### Column mapping

Field names match columns case-insensitively, ignoring underscores — a
`bookName` column fills a `book_name` field automatically. For write-side
mapping to legacy column names, use metadata (names mirror Jakarta
Persistence):

```python
book_name: str | None = field(default=None, metadata={"column": "bookName"})
row_count: int | None = field(default=None, metadata={"transient": True})
```

### Entity configuration (class attributes)

| Attribute | Default | Java counterpart |
|---|---|---|
| `table_name` | class name lowercased | `getTableName()` |
| `key_names` | `("id",)` | `getKeyNames()` |
| `exclusive_fields` | `()` | `getExclusiveFields()` |
| `system_increment` | `False` | `SystemIncrementEntity` |
| `allow_null_value` | `False` | `isAllowNullValue()` |

## Tests

```bash
cd python
PYTHONPATH=src python3 -m pytest tests
```

Runs against in-memory SQLite; no database setup required.
