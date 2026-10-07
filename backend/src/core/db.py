"""Database tables (SQLAlchemy). SQLite for local dev and tests, Postgres in docker-compose."""
from sqlalchemy import Boolean, Float, ForeignKey, Integer, String, create_engine
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, sessionmaker
from sqlalchemy.pool import StaticPool


class Base(DeclarativeBase):
    pass


class Device(Base):
    """One Linda install. `device_id` is a random hashed ID, never a phone number."""
    __tablename__ = "devices"
    device_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    trust: Mapped[float] = mapped_column(Float)
    has_integrity_token: Mapped[bool] = mapped_column(Boolean, default=False)
    first_seen: Mapped[float] = mapped_column(Float)


class Sender(Base):
    """A number that someone reported. One row per sender, status is derived from it."""
    __tablename__ = "senders"
    msisdn: Mapped[str] = mapped_column(String(16), primary_key=True)
    category: Mapped[str | None] = mapped_column(String(32), nullable=True)
    fingerprint: Mapped[str | None] = mapped_column(String(16), nullable=True)
    suspected_until: Mapped[float] = mapped_column(Float, default=0.0)
    confirmed_at: Mapped[float | None] = mapped_column(Float, nullable=True)
    allowlisted: Mapped[bool] = mapped_column(Boolean, default=False)
    updated_at: Mapped[float] = mapped_column(Float, default=0.0)  # drives GET /v1/blocklist?since=


class Report(Base):
    """One report from one phone. There is no message text anywhere in this table, by design."""
    __tablename__ = "reports"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    sender: Mapped[str] = mapped_column(ForeignKey("senders.msisdn"), index=True)
    device_id: Mapped[str] = mapped_column(ForeignKey("devices.device_id"), index=True)
    category: Mapped[str] = mapped_column(String(32))
    confidence: Mapped[float] = mapped_column(Float)
    fingerprint: Mapped[str] = mapped_column(String(16))
    model_version: Mapped[str] = mapped_column(String(32))
    sent_at: Mapped[str] = mapped_column(String(40))  # as the phone claimed; never used for the window
    received_at: Mapped[float] = mapped_column(Float, index=True)  # our clock
    settled: Mapped[bool] = mapped_column(Boolean, default=False)  # trust adjustment already applied


class SecurityEvent(Base):
    """Things worth a human's attention, e.g. a suspected poisoning attempt."""
    __tablename__ = "security_events"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    kind: Mapped[str] = mapped_column(String(32))
    sender: Mapped[str] = mapped_column(String(16))
    detail: Mapped[str] = mapped_column(String(200))
    at: Mapped[float] = mapped_column(Float)


def make_session_factory(database_url: str) -> sessionmaker:
    if database_url.startswith("sqlite"):
        # In-memory SQLite must share one connection or every request sees an empty database.
        engine = create_engine(
            database_url,
            connect_args={"check_same_thread": False},
            poolclass=StaticPool if ":memory:" in database_url else None,
        )
    else:
        engine = create_engine(database_url, pool_pre_ping=True)
    Base.metadata.create_all(engine)
    return sessionmaker(engine, expire_on_commit=False)
