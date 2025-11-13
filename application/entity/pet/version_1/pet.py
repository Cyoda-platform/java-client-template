"""Pet entity for pet store application."""
from pydantic import BaseModel

class Pet(BaseModel):
    """Pet entity."""
    name: str
    species: str
    age: int
