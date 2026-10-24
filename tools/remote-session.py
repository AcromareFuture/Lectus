"""Interactive SSH deployment session. Password stays in process memory."""
import getpass
import hashlib
import json
import sys
import paramiko

HOST_KEY = '96a04cdb62e283db6801e164bd557cc4ab8a3096a37f61806202e82e630043d5'
class PinnedKey(paramiko.MissingHostKeyPolicy):
 def missing_host_key(self, client, hostname, key):
  if hashlib.sha256(key.asbytes()).hexdigest() != HOST_KEY:
   raise paramiko.SSHException('Server host key does not match inventory')

client = paramiko.SSHClient()
client.load_system_host_keys()
client.set_missing_host_key_policy(PinnedKey())
client.connect('39.107.112.161', username='root', password=getpass.getpass('SSH password: '), timeout=30, auth_timeout=60, look_for_keys=False, allow_agent=False)
print('READY', flush=True)
for line in sys.stdin:
 try:
  task = json.loads(line)
  if task.get('close'):
   break
  if 'put' in task:
   with client.open_sftp() as sftp:
    sftp.put(task['put'], task['remote'])
   print('UPLOADED', task['remote'], flush=True)
  elif 'get' in task:
   with client.open_sftp() as sftp:
    sftp.get(task['get'], task['local'])
   print('DOWNLOADED', task['local'], flush=True)
  elif 'command' in task:
   _, stdout, stderr = client.exec_command(task['command'], timeout=task.get('timeout', 120))
   # Drain both streams together, including long-running deployment progress.
   channel = stdout.channel
   while not channel.exit_status_ready() or channel.recv_ready() or channel.recv_stderr_ready():
    if channel.recv_ready():
     print(channel.recv(8192).decode('utf-8', errors='replace'), end='', flush=True)
    if channel.recv_stderr_ready():
     print(channel.recv_stderr(8192).decode('utf-8', errors='replace'), end='', flush=True)
    if not channel.recv_ready() and not channel.recv_stderr_ready():
     import time
     time.sleep(.1)
   print('\nEXIT', channel.recv_exit_status(), flush=True)
 except Exception as error:
  print('ERROR', type(error).__name__, str(error), flush=True)
client.close()
