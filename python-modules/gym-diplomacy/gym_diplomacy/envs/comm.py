# load additional Python module
import socket
import sys

import logging

logging_level = 'DEBUG'
level = getattr(logging, logging_level)
logging.basicConfig(stream=sys.stdout, level=level)
logger = logging.getLogger(__name__)


class LocalSocketServer:
    sock = None
    handler = None

    def __init__(self, port, handler):
        self.handler = handler

        # create TCP (SOCK_STREAM) /IP socket
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

        # retrieve local hostname
        local_hostname = socket.gethostname()

        # get fully qualified hostname
        local_fqdn = socket.getfqdn()

        # get the according IP address
        ip_address = socket.gethostbyname(local_hostname)

        # output hostname, domain name and IP address
        logger.debug("Socket working on %s (%s) with %s" % (local_hostname, local_fqdn, ip_address))

        # bind the socket to the port given
        server_address = (ip_address, port)

        logger.debug('Socket starting up on %s port %s' % server_address)
        self.sock.bind(server_address)

        # listen for incoming connections (server mode) with one connection at a time
        self.sock.listen(1)

        # self.sock.setblocking(False)

    def listen(self):
        while True:
            # wait for a connection
            logger.debug('waiting for a connection')
            connection, client_address = self.sock.accept()

            try:
                # show who connected to us
                logger.debug('connection from {}'.format(client_address))

                data = connection.recv(1024 * 20)

                logger.info("Generating response...")
                connection.send(self.handler.handle(data))
                logger.info("Sent response.")

            finally:
                # Clean up the connection
                connection.close()


class RequestHandler:
    def handle(self, request: bytearray):
        return request


def main_f():
    handler = RequestHandler()
    sock = LocalSocketServer(5000, handler)
    sock.listen()


if __name__ == "__main__":
    main_f()
