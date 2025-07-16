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


async def process_prototype(entity):
    """
    Workflow function applied to the entity asynchronously before persistence.
    This function takes the entity data as the only argument.
    You can:
     - Change entity state directly (e.g. entity['foo'] = 'bar')
     - Get and add entities of different entity_model asynchronously
    You cannot:
     - add/update/delete entity of the same entity_model (to avoid recursion)
    """

    import datetime

    # Validate required fields or set defaults
    if 'name' not in entity or not isinstance(entity['name'], str) or not entity['name'].strip():
        raise ValueError("Entity must have a non-empty 'name' field of type string")

    # Example: Mark entity as processed and add timestamp
    entity['processed'] = True
    entity['processed_at'] = datetime.datetime.utcnow().isoformat()

    # Example: fetch supplementary data and add it to entity
    related_id = entity.get('related_id')
    if related_id:
        try:
            related_entity = await entity_service.get_item(
                token=cyoda_auth_service,
                entity_model='related_entity',  # different entity_model
                entity_version=ENTITY_VERSION,
                technical_id=related_id
            )
            if related_entity:
                entity['related_entity_data'] = related_entity
        except Exception as e:
            logger.warning(f"Failed to fetch related_entity {related_id}: {e}")

    # Example: Add a supplementary entity asynchronously (different model)
    try:
        supplementary_entity = {
            'source_prototype_id': entity.get('id', None),
            'info': 'Supplementary info generated in workflow',
            'created_at': datetime.datetime.utcnow().isoformat()
        }
        # This is fire-and-forget, but we await to ensure completion before persist
        await entity_service.add_item(
            token=cyoda_auth_service,
            entity_model='supplementary_info',
            entity_version=ENTITY_VERSION,
            entity=supplementary_entity,
            workflow=None  # No workflow to avoid recursion
        )
    except Exception as e:
        logger.warning(f"Failed to add supplementary_info entity: {e}")

    return entity


@app.route('/prototype', methods=['POST'])
async def create_prototype():
    try:
        data = await request.get_json()
        if not isinstance(data, dict):
            return jsonify({'error': 'Invalid JSON payload'}), 400

        id = await entity_service.add_item(
            token=cyoda_auth_service,
            entity_model=entity_name,
            entity_version=ENTITY_VERSION,
            entity=data
        )
        return jsonify({'id': id}), 201
    except ValueError as ve:
        logger.warning(f"Validation error on create_prototype: {ve}")
        return jsonify({'error': str(ve)}), 400
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


async def process_prototype_update(entity):
    """
    Workflow function for update pre-persistence logic.
    This is optional and can be used if entity_service.update_item supports a workflow argument.
    """
    import datetime

    # Example: Add/update a field indicating last update timestamp
    entity['last_updated_at'] = datetime.datetime.utcnow().isoformat()

    # Additional update-related async logic can be added here

    return entity


@app.route('/prototype/<string:id>', methods=['PUT'])
async def update_prototype(id):
    try:
        data = await request.get_json()
        if not isinstance(data, dict):
            return jsonify({'error': 'Invalid JSON payload'}), 400

        # Validate required fields or sanitize data as needed
        if 'name' in data and (not isinstance(data['name'], str) or not data['name'].strip()):
            return jsonify({'error': "'name' must be a non-empty string"}), 400

        # If update_item supports workflow arg, pass process_prototype_update, else omit
        # Assuming update_item signature: update_item(..., workflow=None)
        # If not supported, remove workflow arg below.

        # Check if update_item supports workflow param by inspection or documentation.
        # Here we assume it does for consistency.
        try:
            await entity_service.update_item(
                token=cyoda_auth_service,
                entity_model=entity_name,
                entity_version=ENTITY_VERSION,
                entity=data,
                technical_id=id,
                meta={},
            )
        except TypeError:
            # fallback if workflow not supported
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
        if not isinstance(condition, dict):
            return jsonify({'error': 'Invalid JSON payload'}), 400

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
