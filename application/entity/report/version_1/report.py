"""Report entity for pet store application."""
from pydantic import BaseModel

class Report(BaseModel):
    """Report entity."""
    title: str
    content: str
