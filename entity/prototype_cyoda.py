from quart import Blueprint, request, jsonify
import logging
from common.config.config import ENTITY_VERSION
from app_init.app_init import entity_service
from app_init.app_init import BeanFactory
from common.util.condition import Condition, SearchConditionRequest  # assuming these exist for condition construction

factory = BeanFactory(config={'CHAT_REPOSITORY': 'cyoda'})
entity_service = factory.get_services()['entity_service']
cyoda_auth_service = factory.get_services()['cyoda_auth_service']

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)

bp = Blueprint('cyoda_entity_prototype', __name__, url_prefix='/cyoda/items')


@bp.route('', methods=['POST'])
async def create_item():
    data = await request.get_json()
    # data validation and business logic can be preserved here
    try:
        tech_id = await entity_service.add_item(
            token=cyoda_auth_service,
            entity_model="cyoda",
            entity_version=ENTITY_VERSION,
            entity=data
        )
        return jsonify({"id": str(tech_id)}), 201
    except Exception as e:
        logger.exception(e)
        raise


@bp.route('/<string:technical_id>', methods=['GET'])
async def get_item(technical_id: str):
    try:
        item = await entity_service.get_item(
            token=cyoda_auth_service,
            entity_model="cyoda",
            entity_version=ENTITY_VERSION,
            technical_id=technical_id
        )
        if not item:
            return jsonify({"error": "Item not found"}), 404
        return jsonify(item)
    except Exception as e:
        logger.exception(e)
        raise


@bp.route('', methods=['GET'])
async def get_all_items():
    try:
        items = await entity_service.get_items(
            token=cyoda_auth_service,
            entity_model="cyoda",
            entity_version=ENTITY_VERSION,
        )
        return jsonify(items)
    except Exception as e:
        logger.exception(e)
        raise


@bp.route('/search', methods=['POST'])
async def search_items():
    condition = await request.get_json()
    # condition expected as dict compliant with SearchConditionRequest format
    try:
        # pass condition as is since entity_service expects it in that format
        items = await entity_service.get_items_by_condition(
            token=cyoda_auth_service,
            entity_model="cyoda",
            entity_version=ENTITY_VERSION,
            condition=condition
        )
        return jsonify(items)
    except Exception as e:
        logger.exception(e)
        raise


@bp.route('/<string:technical_id>', methods=['PUT'])
async def update_item(technical_id: str):
    data = await request.get_json()
    try:
        updated_id = await entity_service.update_item(
            token=cyoda_auth_service,
            entity_model="cyoda",
            entity_version=ENTITY_VERSION,
            entity=data,
            technical_id=technical_id,
            meta={}
        )
        return jsonify({"id": str(updated_id)})
    except Exception as e:
        logger.exception(e)
        raise


@bp.route('/<string:technical_id>', methods=['DELETE'])
async def delete_item(technical_id: str):
    try:
        deleted_id = await entity_service.delete_item(
            token=cyoda_auth_service,
            entity_model="cyoda",
            entity_version=ENTITY_VERSION,
            technical_id=technical_id,
            meta={}
        )
        return jsonify({"id": str(deleted_id)})
    except Exception as e:
        logger.exception(e)
        raise