import logging
from quart import Quart, request, jsonify
from app_init.app_init import BeanFactory
from common.config.config import ENTITY_VERSION

factory = BeanFactory(config={'CHAT_REPOSITORY': 'cyoda'})
entity_service = factory.get_services()['entity_service']
cyoda_auth_service = factory.get_services()["cyoda_auth_service"]

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)

app = Quart(__name__)

entity_name = "prototype"  # entity name in underscore lowercase


@app.route('/prototype', methods=['POST'])
async def create_prototype():
    try:
        data = await request.get_json()
        # Validate data here if needed
        id = await entity_service.add_item(
            token=cyoda_auth_service,
            entity_model=entity_name,
            entity_version=ENTITY_VERSION,
            entity=data
        )
        return jsonify({'id': id}), 201
    except Exception as e:
        logger.exception(e)
        return jsonify({'error': 'Failed to create prototype'}), 500


@app.route('/prototype/<string:id>', methods=['GET'])
async def get_prototype(id):
    try:
        item = await entity_service.get_item(
            token=cyoda_auth_service,
            entity_model=entity_name,
            entity_version=ENTITY_VERSION,
            technical_id=id
        )
        if not item:
            return jsonify({'error': 'Prototype not found'}), 404
        return jsonify(item), 200
    except Exception as e:
        logger.exception(e)
        return jsonify({'error': 'Failed to retrieve prototype'}), 500


@app.route('/prototype', methods=['GET'])
async def list_prototypes():
    try:
        items = await entity_service.get_items(
            token=cyoda_auth_service,
            entity_model=entity_name,
            entity_version=ENTITY_VERSION
        )
        return jsonify(items), 200
    except Exception as e:
        logger.exception(e)
        return jsonify({'error': 'Failed to retrieve prototypes'}), 500


@app.route('/prototype/<string:id>', methods=['PUT'])
async def update_prototype(id):
    try:
        data = await request.get_json()
        # Validate data here if needed
        await entity_service.update_item(
            token=cyoda_auth_service,
            entity_model=entity_name,
            entity_version=ENTITY_VERSION,
            entity=data,
            technical_id=id,
            meta={}
        )
        return jsonify({'message': 'Prototype updated'}), 200
    except Exception as e:
        logger.exception(e)
        return jsonify({'error': 'Failed to update prototype'}), 500


@app.route('/prototype/<string:id>', methods=['DELETE'])
async def delete_prototype(id):
    try:
        await entity_service.delete_item(
            token=cyoda_auth_service,
            entity_model=entity_name,
            entity_version=ENTITY_VERSION,
            technical_id=id,
            meta={}
        )
        return jsonify({'message': 'Prototype deleted'}), 200
    except Exception as e:
        logger.exception(e)
        return jsonify({'error': 'Failed to delete prototype'}), 500


@app.route('/prototype/search', methods=['POST'])
async def search_prototypes():
    try:
        condition = await request.get_json()
        items = await entity_service.get_items_by_condition(
            token=cyoda_auth_service,
            entity_model=entity_name,
            entity_version=ENTITY_VERSION,
            condition=condition
        )
        return jsonify(items), 200
    except Exception as e:
        logger.exception(e)
        return jsonify({'error': 'Failed to search prototypes'}), 500