from datetime import timezone, datetime
import logging
from quart import Blueprint, request, abort, jsonify
from quart_schema import validate, validate_querystring, tag, operation_id
from app_init.app_init import BeanFactory

logger = logging.getLogger(__name__)

FINAL_STATES = {'FAILURE', 'SUCCESS', 'CANCELLED', 'CANCELLED_BY_USER', 'UNKNOWN', 'FINISHED'}
PROCESSING_STATE = 'PROCESSING'

routes_bp = Blueprint('routes', __name__, url_prefix='/cyoda/items')

factory = BeanFactory(config={'CHAT_REPOSITORY': 'cyoda'})
entity_service = factory.get_services()['entity_service']
cyoda_auth_service = factory.get_services()['cyoda_auth_service']

async def process_cyoda(entity):
    # Add processed timestamp in ISO 8601 UTC format
    entity['processed_timestamp'] = datetime.now(timezone.utc).isoformat()

    try:
        # Fetch supplementary entities from a different entity_model asynchronously
        supplementary_items = await entity_service.get_items(
            token=cyoda_auth_service,
            entity_model="supplementary_model",
            entity_version="1.0"  # or import ENTITY_VERSION if available
        )
        count = len(supplementary_items) if supplementary_items else 0
        entity['supplementary_count'] = count
    except Exception as e:
        logger.error(f"Failed to enrich entity in process_cyoda workflow: {e}")
        # Do not fail the workflow, just log and continue

    # Additional async enrichment or fire-and-forget tasks can be added here safely

    return entity

@routes_bp.route('', methods=['POST'])
async def create_item():
    data = await request.get_json()
    if not data:
        return jsonify({"error": "Missing JSON body"}), 400
    try:
        tech_id = await entity_service.add_item(
            token=cyoda_auth_service,
            entity_model="cyoda",
            entity_version="1.0",  # or import ENTITY_VERSION if available
            entity=data
        )
        return jsonify({"id": str(tech_id)}), 201
    except Exception as e:
        logger.exception("Error in create_item")
        return jsonify({"error": "Internal server error"}), 500

@routes_bp.route('/<string:technical_id>', methods=['GET'])
async def get_item(technical_id: str):
    if not technical_id:
        return jsonify({"error": "Missing technical_id"}), 400
    try:
        item = await entity_service.get_item(
            token=cyoda_auth_service,
            entity_model="cyoda",
            entity_version="1.0",  # or import ENTITY_VERSION if available
            technical_id=technical_id
        )
        if not item:
            return jsonify({"error": "Item not found"}), 404
        return jsonify(item)
    except Exception as e:
        logger.exception("Error in get_item")
        return jsonify({"error": "Internal server error"}), 500

@routes_bp.route('', methods=['GET'])
async def get_all_items():
    try:
        items = await entity_service.get_items(
            token=cyoda_auth_service,
            entity_model="cyoda",
            entity_version="1.0"  # or import ENTITY_VERSION if available
        )
        return jsonify(items)
    except Exception as e:
        logger.exception("Error in get_all_items")
        return jsonify({"error": "Internal server error"}), 500

@routes_bp.route('/search', methods=['POST'])
async def search_items():
    condition = await request.get_json()
    if not condition:
        return jsonify({"error": "Missing search condition JSON"}), 400
    try:
        items = await entity_service.get_items_by_condition(
            token=cyoda_auth_service,
            entity_model="cyoda",
            entity_version="1.0",  # or import ENTITY_VERSION if available
            condition=condition
        )
        return jsonify(items)
    except Exception as e:
        logger.exception("Error in search_items")
        return jsonify({"error": "Internal server error"}), 500

@routes_bp.route('/<string:technical_id>', methods=['PUT'])
async def update_item(technical_id: str):
    if not technical_id:
        return jsonify({"error": "Missing technical_id"}), 400
    data = await request.get_json()
    if not data:
        return jsonify({"error": "Missing JSON body"}), 400
    try:
        updated_id = await entity_service.update_item(
            token=cyoda_auth_service,
            entity_model="cyoda",
            entity_version="1.0",  # or import ENTITY_VERSION if available
            entity=data,
            technical_id=technical_id,
            meta={}
        )
        return jsonify({"id": str(updated_id)})
    except Exception as e:
        logger.exception("Error in update_item")
        return jsonify({"error": "Internal server error"}), 500

@routes_bp.route('/<string:technical_id>', methods=['DELETE'])
async def delete_item(technical_id: str):
    if not technical_id:
        return jsonify({"error": "Missing technical_id"}), 400
    try:
        deleted_id = await entity_service.delete_item(
            token=cyoda_auth_service,
            entity_model="cyoda",
            entity_version="1.0",  # or import ENTITY_VERSION if available
            technical_id=technical_id,
            meta={}
        )
        return jsonify({"id": str(deleted_id)})
    except Exception as e:
        logger.exception("Error in delete_item")
        return jsonify({"error": "Internal server error"}), 500