"""Pet routes."""
from quart import Blueprint

pets_bp = Blueprint('pets', __name__)

@pets_bp.route('/pets', methods=['GET'])
async def list_pets():
    return {"pets": []}
