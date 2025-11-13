"""Report routes."""
from quart import Blueprint

reports_bp = Blueprint('reports', __name__)

@reports_bp.route('/reports', methods=['GET'])
async def list_reports():
    return {"reports": []}
