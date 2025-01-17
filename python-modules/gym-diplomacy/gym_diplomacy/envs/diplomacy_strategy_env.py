import logging

import numpy as np
from gym import spaces

from gym_diplomacy.envs import diplomacy_env
from gym_diplomacy.envs.proto_message import proto_message_pb2

logging_level = 'DEBUG'
level = getattr(logging, logging_level)
logger = logging.getLogger(__name__)
logger.setLevel(level)

### LEVELS OF LOGGING (in increasing order of severity)
# DEBUG	    Detailed information, typically of interest only when diagnosing problems.
# INFO	    Confirmation that things are working as expected.
# WARNING	An indication that something unexpected happened, or indicative of some problem in the near future
# (e.g. ‘disk space low’). The software is still working as expected.
# ERROR	    Due to a more serious problem, the software has not been able to perform some function.
# CRITICAL	A serious error, indicating that the program itself may be unable to continue running.

### CONSTANTS
NUMBER_OF_ACTIONS = 3
NUMBER_OF_PLAYERS = 2  # 7
NUMBER_OF_PROVINCES = 8  # 75


def observation_data_to_observation(observation_data: proto_message_pb2.ObservationData) -> np.array:
    """
    This function takes a Protobuf ObservationData and generates the necessary information for the agent to act.

    :param observation_data: A Protobug ObservationData object.
    :return: A list with the structure [observation, reward, done, info]. Observation is an np array, reward is a float,
    done is a boolean and info is a string.
    """
    number_of_provinces = len(observation_data.provinces)

    if number_of_provinces != NUMBER_OF_PROVINCES:
        raise ValueError("Number of provinces is not consistent. Constant variable is '{}' while received number of "
                         "provinces is '{}'.".format(NUMBER_OF_PROVINCES, number_of_provinces))

    observation = np.zeros(number_of_provinces * 3)

    for province in observation_data.provinces:
        # simply for type hint and auto-completion
        province: proto_message_pb2.ProvinceData = province

        # id - 1 because the ids begin at 1
        observation[(province.id - 1) * 3] = province.owner
        observation[(province.id - 1) * 3 + 1] = province.sc
        observation[(province.id - 1) * 3 + 2] = province.unit

    reward = observation_data.previousActionReward
    done = observation_data.done
    info = {}

    return observation, reward, done, info


def action_to_orders_data(action, state) -> proto_message_pb2.OrdersData:
    """
    Transforms the action list generated by the model into a OrdersData object that will be sent to Bandana.
    :param action: The list of the agent action.
    :return: OrdersData object with the representation of the set of orders.
    """
    # player_units = get_player_units(state)
    orders_data: proto_message_pb2.OrdersData = proto_message_pb2.OrdersData()
    for unit, order in enumerate(action):
        # if unit in player_units:
        if order == 0:
            clean_order = [unit, 0, 0]
        else:
            order = order - 1
            order_type, destination = divmod(order, NUMBER_OF_PROVINCES)
            clean_order = [unit, order_type + 1, destination]

        new_order = orders_data.orders.add()
        new_order.start = int(clean_order[0])
        new_order.action = int(clean_order[1])
        new_order.destination = int(clean_order[2])
    return orders_data


def get_player_units(state):
    player = 1
    units = state[2::3]
    player_units = [i for i, unit in enumerate(units) if unit == player]
    return player_units


class DiplomacyStrategyEnv(diplomacy_env.DiplomacyEnv):

    def render(self, mode='human'):
        raise NotImplementedError

    def _init_observation_space(self):
        """
        Observation space: [[province_id, owner, is_supply_center, has_unit] * number of provinces]
        The last 2 values represent the player id and the province to pick the order.
        Eg: If observation_space[2] is [5, 0, 0], then the second province belongs to player 5, is NOT a SC, and does NOT have a unit.
        """
        observation_space_description = []
        for i in range(NUMBER_OF_PROVINCES):
            observation_space_description.extend([NUMBER_OF_PLAYERS, 2, NUMBER_OF_PLAYERS])

        self.observation_space = spaces.MultiDiscrete(observation_space_description)

    def _init_action_space(self):
        """
        An action represents an order for a unit.
        Action space: [Order type for the unit, Destination province]
        Eg: Action [2, 5] proposes an order of type 2 related to the province with id 5.
        """
        action_space_description = []
        action_space_row = [1 + (NUMBER_OF_ACTIONS - 1) * NUMBER_OF_PROVINCES]
        for i in range(NUMBER_OF_PROVINCES):
            action_space_description.extend(action_space_row)
        self.action_space = spaces.MultiDiscrete(action_space_description)

    def handle_request(self, request: bytearray) -> bytes:
        request_data: proto_message_pb2.BandanaRequest = proto_message_pb2.BandanaRequest()
        request_data.ParseFromString(request)

        if request_data.type is proto_message_pb2.BandanaRequest.INVALID:
            raise ValueError("Type of BandanaRequest is INVALID.", request_data)

        observation_data: proto_message_pb2.ObservationData = request_data.observation
        self.observation, self.reward, self.done, self.info = observation_data_to_observation(observation_data)

        response_data: proto_message_pb2.DiplomacyGymOrdersResponse = proto_message_pb2.DiplomacyGymOrdersResponse()
        response_data.type = proto_message_pb2.DiplomacyGymOrdersResponse.VALID

        self.waiting_for_action = True
        while self.waiting_for_action:
            if self.done or self.terminate:
                # Return empty deal just to finalize program
                logger.debug("Sending empty deal to finalize program.")
                return response_data.SerializeToString()

        self.received_first_observation = True

        orders_data: proto_message_pb2.OrdersData = action_to_orders_data(self.action, self.observation)
        response_data.orders.CopyFrom(orders_data)

        return response_data.SerializeToString()


if __name__ == "__main__":
    gym = DiplomacyStrategyEnv()
