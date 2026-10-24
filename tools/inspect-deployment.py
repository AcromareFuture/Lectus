"""Read-only deployment inventory; credentials are entered via terminal, never saved."""
import getpass
import hashlib
import paramiko

client = paramiko.SSHClient()
client.load_system_host_keys()
# Initial inventory only. Print the key so subsequent deployment can pin it.
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect('39.107.112.161', username='root', password=getpass.getpass('SSH password: '), timeout=12, auth_timeout=12, look_for_keys=False, allow_agent=False)
print('Host key SHA256:', hashlib.sha256(client.get_transport().get_remote_server_key().asbytes()).hexdigest())
for command in [
 'uname -sr; uptime',
 'ss -lntp | head -25',
 'docker ps --format "{{.Names}} {{.Image}} {{.Ports}}"',
 "nginx -T 2>/dev/null | grep -E 'server_name|listen |root |proxy_pass|ssl_certificate'",
 'ls -ld /var/www/* /opt/* /www/wwwroot/* 2>/dev/null',
 'command -v java; command -v nginx; command -v docker; command -v certbot; free -m; df -h /',
]:
 print('\nCHECK:', command)
 _, stdout, stderr = client.exec_command(command, timeout=20)
 print(stdout.read().decode('utf-8', errors='replace'))
 print(stderr.read().decode('utf-8', errors='replace')[:400])
client.close()
