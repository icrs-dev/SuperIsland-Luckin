
import socket
import sys

with open('proxy_output.txt', 'w') as f:
    try:
        server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server.bind(('127.0.0.1', 8080))
        server.listen(1)
        f.write('Waiting for connection...\n')
        f.flush()
        
        conn, addr = server.accept()
        data = conn.recv(8192)
        f.write('--- RECEIVED ---\n')
        f.write(data.decode('utf-8', errors='ignore'))
        f.write('\n----------------\n')
        f.flush()
        conn.send(b'HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\n{}')
        conn.close()
        server.close()
    except Exception as e:
        f.write(str(e))

