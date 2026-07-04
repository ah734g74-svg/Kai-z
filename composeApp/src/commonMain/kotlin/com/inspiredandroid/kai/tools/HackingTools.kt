package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema

/**
 * HackingTools — مجموعة أدوات اختبار الاختراق والأمن الهجومي لمساعدة الذكاء الاصطناعي
 * على تنفيذ مهام الأمن السيبراني عبر الصدفة (ShellCommandTool).
 *
 * تشمل الأدوات: Nmap, SQLmap, Gobuster, Nikto, Hydra, Metasploit,
 * Hashcat, John the Ripper, Netcat, DNS Recon, WPScan,
 * Enum4linux, ARP Scan, HTTP Security Headers, SSL/TLS Scanner.
 *
 * ملاحظة: هذه الأدوات مخصصة للاستخدام القانوني والأخلاقي فقط (Ethical Hacking / Penetration Testing).
 */
object HackingTools {

    // ─────────────────────────────────────────────────────────────────
    // 1. Nmap — مسح الشبكات واكتشاف الخدمات
    // ─────────────────────────────────────────────────────────────────
    val nmapScanTool = object : Tool {
        override val schema = ToolSchema(
            name = "nmap_scan",
            description = "Nmap network scanner: discover hosts, open ports, running services, OS fingerprinting, and known vulnerabilities. Scan types: quick (fast 100 ports), full (OS+service detection), ports (all 65535), vuln (NSE vuln scripts), stealth (SYN -sS), udp (UDP scan). Use only on systems you own or have explicit permission to test.",
            parameters = mapOf(
                "target" to ParameterSchema(
                    "string",
                    "Target IP, hostname, or CIDR range (e.g. 192.168.1.1, example.com, 10.0.0.0/24)",
                    true
                ),
                "scan_type" to ParameterSchema(
                    "string",
                    "Scan type: quick | full | ports | vuln | stealth | udp (default: quick)",
                    false
                ),
                "ports" to ParameterSchema(
                    "string",
                    "Specific ports or ranges to scan, e.g. 80,443,8080 or 1-1024 (optional)",
                    false
                ),
                "output_file" to ParameterSchema(
                    "string",
                    "Save results to this file path (optional, e.g. /tmp/nmap_result.txt)",
                    false
                ),
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val target = args["target"]?.toString()
                ?: return mapOf("success" to false, "error" to "target is required")
            val scanType = args["scan_type"]?.toString() ?: "quick"
            val ports = args["ports"]?.toString()
            val outputFile = args["output_file"]?.toString()

            val portFlag = if (ports != null) "-p $ports" else ""
            val outputFlag = if (outputFile != null) "-oN \"$outputFile\"" else ""

            val baseCmd = when (scanType) {
                "quick"   -> "nmap -T4 -F $portFlag $outputFlag $target"
                "full"    -> "nmap -T4 -A -v $portFlag $outputFlag $target"
                "ports"   -> "nmap -p- $outputFlag $target"
                "vuln"    -> "nmap --script vuln $portFlag $outputFlag $target"
                "stealth" -> "nmap -sS -T2 $portFlag $outputFlag $target"
                "udp"     -> "nmap -sU $portFlag $outputFlag $target"
                else      -> "nmap $portFlag $outputFlag $target"
            }.trim().replace(Regex("\\s+"), " ")

            return mapOf(
                "success" to true,
                "tool" to "nmap",
                "command" to baseCmd,
                "note" to "Run this command via execute_shell_command in the Linux sandbox."
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 2. SQLmap — اكتشاف واستغلال ثغرات SQL Injection
    // ─────────────────────────────────────────────────────────────────
    val sqlmapTool = object : Tool {
        override val schema = ToolSchema(
            name = "sqlmap_scan",
            description = "SQLmap: automated SQL injection detection and exploitation. Supports GET/POST parameters, cookies, headers. Use only on systems you own or have explicit written permission to test.",
            parameters = mapOf(
                "url" to ParameterSchema(
                    "string",
                    "Target URL with injectable parameter, e.g. http://example.com/page.php?id=1",
                    true
                ),
                "level" to ParameterSchema("integer", "Test level 1-5 (default: 1)", false),
                "risk"  to ParameterSchema("integer", "Risk level 1-3 (default: 1)", false),
                "data"  to ParameterSchema("string", "POST body data, e.g. user=admin&pass=test", false),
                "cookie" to ParameterSchema("string", "HTTP cookie header value", false),
                "dbs"   to ParameterSchema("boolean", "Enumerate databases (default: false)", false),
                "dump"  to ParameterSchema("boolean", "Dump database tables (default: false)", false),
                "dbms"  to ParameterSchema("string", "Force DBMS type: mysql | postgresql | mssql | oracle | sqlite", false),
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val url    = args["url"]?.toString() ?: return mapOf("success" to false, "error" to "url is required")
            val level  = args["level"]?.toString() ?: "1"
            val risk   = args["risk"]?.toString() ?: "1"
            val data   = args["data"]?.toString()
            val cookie = args["cookie"]?.toString()
            val dbs    = args["dbs"] as? Boolean ?: false
            val dump   = args["dump"] as? Boolean ?: false
            val dbms   = args["dbms"]?.toString()

            val sb = StringBuilder("sqlmap -u \"$url\" --level=$level --risk=$risk --batch")
            if (data   != null) sb.append(" --data=\"$data\"")
            if (cookie != null) sb.append(" --cookie=\"$cookie\"")
            if (dbms   != null) sb.append(" --dbms=$dbms")
            if (dbs)            sb.append(" --dbs")
            if (dump)           sb.append(" --dump")

            return mapOf(
                "success" to true,
                "tool" to "sqlmap",
                "command" to sb.toString(),
                "note" to "Run this command via execute_shell_command in the Linux sandbox."
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 3. Gobuster — اكتشاف الدلائل والملفات المخفية
    // ─────────────────────────────────────────────────────────────────
    val gobusterTool = object : Tool {
        override val schema = ToolSchema(
            name = "gobuster_scan",
            description = "Gobuster: brute-force directories, files, DNS subdomains, and virtual hosts on web servers. Modes: dir (directory/file), dns (subdomain), vhost (virtual host).",
            parameters = mapOf(
                "url"      to ParameterSchema("string", "Target URL, e.g. http://example.com/", true),
                "mode"     to ParameterSchema("string", "Mode: dir | dns | vhost (default: dir)", false),
                "wordlist" to ParameterSchema("string", "Path to wordlist file (default: /usr/share/wordlists/dirb/common.txt)", false),
                "extensions" to ParameterSchema("string", "File extensions to search, e.g. php,html,txt (dir mode only)", false),
                "threads"  to ParameterSchema("integer", "Number of concurrent threads (default: 10)", false),
                "status_codes" to ParameterSchema("string", "HTTP status codes to show, e.g. 200,301,302", false),
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val url        = args["url"]?.toString() ?: return mapOf("success" to false, "error" to "url is required")
            val mode       = args["mode"]?.toString() ?: "dir"
            val wordlist   = args["wordlist"]?.toString() ?: "/usr/share/wordlists/dirb/common.txt"
            val extensions = args["extensions"]?.toString()
            val threads    = args["threads"]?.toString() ?: "10"
            val statusCodes = args["status_codes"]?.toString() ?: "200,204,301,302,307,401,403"

            val sb = StringBuilder("gobuster $mode -u \"$url\" -w \"$wordlist\" -t $threads")
            if (mode == "dir") {
                sb.append(" -s \"$statusCodes\"")
                if (extensions != null) sb.append(" -x $extensions")
            }

            return mapOf(
                "success" to true,
                "tool" to "gobuster",
                "command" to sb.toString(),
                "note" to "Run this command via execute_shell_command in the Linux sandbox."
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 4. Nikto — فحص ثغرات خوادم الويب
    // ─────────────────────────────────────────────────────────────────
    val niktoTool = object : Tool {
        override val schema = ToolSchema(
            name = "nikto_scan",
            description = "Nikto: web server vulnerability scanner. Detects dangerous files, outdated software, misconfigurations, and common CVEs in web servers (Apache, Nginx, IIS, etc.).",
            parameters = mapOf(
                "host"    to ParameterSchema("string", "Target host or URL, e.g. http://example.com or 192.168.1.1", true),
                "port"    to ParameterSchema("integer", "Target port (default: 80 for HTTP, 443 for HTTPS)", false),
                "ssl"     to ParameterSchema("boolean", "Use SSL/HTTPS (default: false)", false),
                "output"  to ParameterSchema("string", "Save report to file path (optional)", false),
                "tuning"  to ParameterSchema("string", "Scan tuning: 1=XSS, 2=File Upload, 3=Misconfig, 4=Info Disclosure, 5=RCE, 7=SQL, 8=Auth Bypass (default: all)", false),
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val host   = args["host"]?.toString() ?: return mapOf("success" to false, "error" to "host is required")
            val port   = args["port"]?.toString()
            val ssl    = args["ssl"] as? Boolean ?: false
            val output = args["output"]?.toString()
            val tuning = args["tuning"]?.toString()

            val sb = StringBuilder("nikto -h \"$host\"")
            if (port   != null) sb.append(" -p $port")
            if (ssl)            sb.append(" -ssl")
            if (output != null) sb.append(" -o \"$output\"")
            if (tuning != null) sb.append(" -Tuning $tuning")

            return mapOf(
                "success" to true,
                "tool" to "nikto",
                "command" to sb.toString(),
                "note" to "Run this command via execute_shell_command in the Linux sandbox."
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 5. Hydra — هجمات القوة الغاشمة على بروتوكولات المصادقة
    // ─────────────────────────────────────────────────────────────────
    val hydraTool = object : Tool {
        override val schema = ToolSchema(
            name = "hydra_brute_force",
            description = "Hydra: fast network login cracker supporting SSH, FTP, HTTP, SMTP, RDP, SMB, MySQL, VNC, Telnet, and more. Use only on systems you own or have explicit written permission to test.",
            parameters = mapOf(
                "target"    to ParameterSchema("string", "Target IP or hostname", true),
                "protocol"  to ParameterSchema("string", "Protocol: ssh | ftp | http-get | http-post-form | smtp | rdp | smb | mysql | vnc | telnet", true),
                "username"  to ParameterSchema("string", "Single username to test", false),
                "username_file" to ParameterSchema("string", "Path to username wordlist file", false),
                "password_file" to ParameterSchema("string", "Path to password wordlist file (default: /usr/share/wordlists/rockyou.txt)", false),
                "port"      to ParameterSchema("integer", "Custom port (optional)", false),
                "threads"   to ParameterSchema("integer", "Number of parallel tasks (default: 16)", false),
                "http_path" to ParameterSchema("string", "HTTP path for http-get/http-post-form (e.g. /login.php)", false),
                "http_form_data" to ParameterSchema("string", "POST form data with ^USER^ and ^PASS^ placeholders, e.g. user=^USER^&pass=^PASS^:Invalid login", false),
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val target    = args["target"]?.toString() ?: return mapOf("success" to false, "error" to "target is required")
            val protocol  = args["protocol"]?.toString() ?: return mapOf("success" to false, "error" to "protocol is required")
            val username  = args["username"]?.toString()
            val usernameFile = args["username_file"]?.toString()
            val passwordFile = args["password_file"]?.toString() ?: "/usr/share/wordlists/rockyou.txt"
            val port      = args["port"]?.toString()
            val threads   = args["threads"]?.toString() ?: "16"
            val httpPath  = args["http_path"]?.toString() ?: "/"
            val httpFormData = args["http_form_data"]?.toString()

            val userFlag = when {
                username != null     -> "-l \"$username\""
                usernameFile != null -> "-L \"$usernameFile\""
                else                 -> "-l admin"
            }
            val portFlag = if (port != null) "-s $port" else ""

            val sb = StringBuilder("hydra -t $threads $userFlag -P \"$passwordFile\" $portFlag $target")
            when (protocol) {
                "http-post-form" -> {
                    val formData = httpFormData ?: "user=^USER^&pass=^PASS^:Invalid"
                    sb.append(" http-post-form \"$httpPath:$formData\"")
                }
                "http-get" -> sb.append(" http-get \"$httpPath\"")
                else       -> sb.append(" $protocol")
            }

            return mapOf(
                "success" to true,
                "tool" to "hydra",
                "command" to sb.toString().trim().replace(Regex("\\s+"), " "),
                "note" to "Run this command via execute_shell_command in the Linux sandbox."
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 6. Hashcat — كسر كلمات المرور المشفرة
    // ─────────────────────────────────────────────────────────────────
    val hashcatTool = object : Tool {
        override val schema = ToolSchema(
            name = "hashcat_crack",
            description = "Hashcat: GPU-accelerated password recovery. Hash types: 0=MD5, 100=SHA1, 1400=SHA256, 1800=sha512crypt, 1000=NTLM, 3200=bcrypt, 500=md5crypt. Attack modes: 0=dictionary, 3=brute-force mask.",
            parameters = mapOf(
                "hash_file"  to ParameterSchema("string", "Path to file containing hashes (one per line)", true),
                "hash_type"  to ParameterSchema("integer", "Hash type number (e.g. 0 for MD5, 1000 for NTLM, 1800 for sha512crypt)", true),
                "attack_mode" to ParameterSchema("integer", "Attack mode: 0=dictionary, 3=brute-force mask (default: 0)", false),
                "wordlist"   to ParameterSchema("string", "Path to wordlist (default: /usr/share/wordlists/rockyou.txt)", false),
                "mask"       to ParameterSchema("string", "Mask for brute-force mode, e.g. ?a?a?a?a?a?a?a?a (mode 3 only)", false),
                "rules"      to ParameterSchema("string", "Path to rules file (optional, e.g. /usr/share/hashcat/rules/best64.rule)", false),
                "output_file" to ParameterSchema("string", "Save cracked hashes to file (optional)", false),
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val hashFile   = args["hash_file"]?.toString() ?: return mapOf("success" to false, "error" to "hash_file is required")
            val hashType   = args["hash_type"]?.toString() ?: return mapOf("success" to false, "error" to "hash_type is required")
            val attackMode = args["attack_mode"]?.toString() ?: "0"
            val wordlist   = args["wordlist"]?.toString() ?: "/usr/share/wordlists/rockyou.txt"
            val mask       = args["mask"]?.toString()
            val rules      = args["rules"]?.toString()
            val outputFile = args["output_file"]?.toString()

            val sb = StringBuilder("hashcat -m $hashType -a $attackMode \"$hashFile\"")
            if (attackMode == "3" && mask != null) sb.append(" \"$mask\"")
            else sb.append(" \"$wordlist\"")
            if (rules      != null) sb.append(" -r \"$rules\"")
            if (outputFile != null) sb.append(" -o \"$outputFile\"")
            sb.append(" --force")

            return mapOf(
                "success" to true,
                "tool" to "hashcat",
                "command" to sb.toString(),
                "note" to "Run this command via execute_shell_command in the Linux sandbox."
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 7. John the Ripper — كسر كلمات المرور
    // ─────────────────────────────────────────────────────────────────
    val johnTool = object : Tool {
        override val schema = ToolSchema(
            name = "john_crack",
            description = "John the Ripper: password cracker supporting many hash formats. Modes: single (fast), wordlist (dictionary), incremental (brute-force). Automatically detects hash format or specify with format parameter.",
            parameters = mapOf(
                "hash_file" to ParameterSchema("string", "Path to file containing hashes", true),
                "wordlist"  to ParameterSchema("string", "Path to wordlist (optional, uses built-in if omitted)", false),
                "format"    to ParameterSchema("string", "Hash format: md5crypt | sha512crypt | bcrypt | nt | lm | raw-md5 | raw-sha1 | etc.", false),
                "rules"     to ParameterSchema("string", "Apply mangling rules: yes | no (default: no)", false),
                "show"      to ParameterSchema("boolean", "Show already cracked passwords (default: false)", false),
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val hashFile = args["hash_file"]?.toString() ?: return mapOf("success" to false, "error" to "hash_file is required")
            val wordlist = args["wordlist"]?.toString()
            val format   = args["format"]?.toString()
            val rules    = args["rules"]?.toString()
            val show     = args["show"] as? Boolean ?: false

            val sb = StringBuilder("john \"$hashFile\"")
            if (wordlist != null) sb.append(" --wordlist=\"$wordlist\"")
            if (format   != null) sb.append(" --format=$format")
            if (rules == "yes")   sb.append(" --rules")
            if (show)             sb.append(" --show")

            return mapOf(
                "success" to true,
                "tool" to "john",
                "command" to sb.toString(),
                "note" to "Run this command via execute_shell_command in the Linux sandbox."
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 8. Netcat — أداة الشبكة متعددة الاستخدامات
    // ─────────────────────────────────────────────────────────────────
    val netcatTool = object : Tool {
        override val schema = ToolSchema(
            name = "netcat_tool",
            description = "Netcat (nc): TCP/UDP Swiss Army Knife. Modes: connect (client), listen (server), port_scan, banner_grab, reverse_shell_listener.",
            parameters = mapOf(
                "mode"    to ParameterSchema("string", "Mode: connect | listen | port_scan | banner_grab | reverse_shell_listener (default: connect)", true),
                "host"    to ParameterSchema("string", "Target host (required for connect, banner_grab, port_scan)", false),
                "port"    to ParameterSchema("integer", "Target or listening port", true),
                "message" to ParameterSchema("string", "Message to send (connect mode, optional)", false),
                "port_range" to ParameterSchema("string", "Port range for scanning, e.g. 1-1024 (port_scan mode)", false),
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val mode      = args["mode"]?.toString() ?: "connect"
            val host      = args["host"]?.toString()
            val port      = args["port"]?.toString() ?: return mapOf("success" to false, "error" to "port is required")
            val message   = args["message"]?.toString()
            val portRange = args["port_range"]?.toString()

            val command = when (mode) {
                "connect" -> {
                    if (host == null) return mapOf("success" to false, "error" to "host is required for connect mode")
                    if (message != null) "echo \"$message\" | nc $host $port"
                    else "nc $host $port"
                }
                "listen"   -> "nc -lvnp $port"
                "port_scan" -> {
                    if (host == null) return mapOf("success" to false, "error" to "host is required for port_scan mode")
                    val range = portRange ?: port
                    "nc -zv $host $range 2>&1"
                }
                "banner_grab" -> {
                    if (host == null) return mapOf("success" to false, "error" to "host is required for banner_grab mode")
                    "echo '' | nc -w 3 $host $port"
                }
                "reverse_shell_listener" -> "nc -lvnp $port"
                else -> if (host != null) "nc $host $port" else "nc -lvnp $port"
            }

            return mapOf(
                "success" to true,
                "tool" to "netcat",
                "command" to command,
                "note" to "Run this command via execute_shell_command in the Linux sandbox."
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 9. DNS Recon — استطلاع DNS والمعلومات (OSINT)
    // ─────────────────────────────────────────────────────────────────
    val dnsReconTool = object : Tool {
        override val schema = ToolSchema(
            name = "dns_recon",
            description = "DNS reconnaissance and WHOIS lookup. Modes: whois, dig (DNS lookup), nslookup, host, dnsenum (full DNS enumeration), dnsrecon (advanced subdomain discovery).",
            parameters = mapOf(
                "domain" to ParameterSchema("string", "Target domain or IP address", true),
                "mode"   to ParameterSchema("string", "Mode: whois | dig | nslookup | host | dnsenum | dnsrecon (default: whois)", false),
                "record_type" to ParameterSchema("string", "DNS record type for dig/nslookup: A | AAAA | MX | NS | TXT | SOA | CNAME | ANY (default: A)", false),
                "wordlist" to ParameterSchema("string", "Subdomain wordlist for dnsenum/dnsrecon (optional)", false),
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val domain     = args["domain"]?.toString() ?: return mapOf("success" to false, "error" to "domain is required")
            val mode       = args["mode"]?.toString() ?: "whois"
            val recordType = args["record_type"]?.toString() ?: "A"
            val wordlist   = args["wordlist"]?.toString()

            val command = when (mode) {
                "whois"    -> "whois $domain"
                "dig"      -> "dig $recordType $domain"
                "nslookup" -> "nslookup -type=$recordType $domain"
                "host"     -> "host -t $recordType $domain"
                "dnsenum"  -> {
                    val wl = if (wordlist != null) "--dnsserver 8.8.8.8 -f \"$wordlist\"" else ""
                    "dnsenum $wl $domain".trim()
                }
                "dnsrecon" -> {
                    val wl = if (wordlist != null) "-D \"$wordlist\" -t brt" else "-t std"
                    "dnsrecon -d $domain $wl"
                }
                else -> "whois $domain"
            }

            return mapOf(
                "success" to true,
                "tool" to "dns_recon",
                "command" to command,
                "note" to "Run this command via execute_shell_command in the Linux sandbox."
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 10. WPScan — فحص ثغرات WordPress
    // ─────────────────────────────────────────────────────────────────
    val wpscanTool = object : Tool {
        override val schema = ToolSchema(
            name = "wpscan",
            description = "WPScan: WordPress vulnerability scanner. Enumerates plugins, themes, users, and known CVEs. Use only on WordPress sites you own or have explicit written permission to test.",
            parameters = mapOf(
                "url"         to ParameterSchema("string", "Target WordPress site URL, e.g. http://example.com/", true),
                "enumerate"   to ParameterSchema("string", "Enumerate: u=users, p=plugins, t=themes, vp=vulnerable plugins, ap=all plugins (default: vp,u)", false),
                "wordlist"    to ParameterSchema("string", "Password wordlist for user brute-force (optional)", false),
                "api_token"   to ParameterSchema("string", "WPScan API token for vulnerability data (optional)", false),
                "aggressive"  to ParameterSchema("boolean", "Use aggressive detection mode (default: false)", false),
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val url        = args["url"]?.toString() ?: return mapOf("success" to false, "error" to "url is required")
            val enumerate  = args["enumerate"]?.toString() ?: "vp,u"
            val wordlist   = args["wordlist"]?.toString()
            val apiToken   = args["api_token"]?.toString()
            val aggressive = args["aggressive"] as? Boolean ?: false

            val sb = StringBuilder("wpscan --url \"$url\" --enumerate $enumerate")
            if (wordlist   != null) sb.append(" --passwords \"$wordlist\"")
            if (apiToken   != null) sb.append(" --api-token \"$apiToken\"")
            if (aggressive)         sb.append(" --plugins-detection aggressive --themes-detection aggressive")

            return mapOf(
                "success" to true,
                "tool" to "wpscan",
                "command" to sb.toString(),
                "note" to "Run this command via execute_shell_command in the Linux sandbox."
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 11. Enum4linux — استطلاع أنظمة Windows/Samba
    // ─────────────────────────────────────────────────────────────────
    val enum4linuxTool = object : Tool {
        override val schema = ToolSchema(
            name = "enum4linux_scan",
            description = "Enum4linux: enumerate information from Windows and Samba systems (users, shares, groups, policies, OS info). Useful for SMB/NetBIOS reconnaissance during internal network penetration tests.",
            parameters = mapOf(
                "target"  to ParameterSchema("string", "Target IP address or hostname", true),
                "options" to ParameterSchema("string", "Options: -a (all), -U (users), -S (shares), -G (groups), -P (password policy), -o (OS info) (default: -a)", false),
                "username" to ParameterSchema("string", "Username for authenticated scan (optional)", false),
                "password" to ParameterSchema("string", "Password for authenticated scan (optional)", false),
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val target   = args["target"]?.toString() ?: return mapOf("success" to false, "error" to "target is required")
            val options  = args["options"]?.toString() ?: "-a"
            val username = args["username"]?.toString()
            val password = args["password"]?.toString()

            val authFlag = if (username != null && password != null) "-u \"$username\" -p \"$password\"" else ""
            val command  = "enum4linux $options $authFlag $target".trim().replace(Regex("\\s+"), " ")

            return mapOf(
                "success" to true,
                "tool" to "enum4linux",
                "command" to command,
                "note" to "Run this command via execute_shell_command in the Linux sandbox."
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 12. ARP Scan — اكتشاف الأجهزة على الشبكة المحلية
    // ─────────────────────────────────────────────────────────────────
    val arpScanTool = object : Tool {
        override val schema = ToolSchema(
            name = "arp_scan",
            description = "ARP Scan: discover live hosts on a local network using ARP requests. Much faster than Nmap for local network host discovery. Requires root/sudo privileges.",
            parameters = mapOf(
                "network"   to ParameterSchema("string", "Network range in CIDR notation (e.g. 192.168.1.0/24) or use 'localnet' for auto-detect", true),
                "interface" to ParameterSchema("string", "Network interface to use (e.g. eth0, wlan0) — optional", false),
                "retry"     to ParameterSchema("integer", "Number of retries per host (default: 2)", false),
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val network   = args["network"]?.toString() ?: return mapOf("success" to false, "error" to "network is required")
            val iface     = args["interface"]?.toString()
            val retry     = args["retry"]?.toString() ?: "2"

            val networkFlag = if (network == "localnet") "--localnet" else network
            val ifaceFlag   = if (iface != null) "-I $iface" else ""
            val command     = "sudo arp-scan --retry=$retry $ifaceFlag $networkFlag".trim().replace(Regex("\\s+"), " ")

            return mapOf(
                "success" to true,
                "tool" to "arp-scan",
                "command" to command,
                "note" to "Run this command via execute_shell_command in the Linux sandbox."
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 13. Metasploit — إطار اختبار الاختراق الشامل
    // ─────────────────────────────────────────────────────────────────
    val metasploitTool = object : Tool {
        override val schema = ToolSchema(
            name = "metasploit_command",
            description = "Metasploit Framework: generate msfconsole resource scripts for exploitation, post-exploitation, and payload generation via msfvenom. Actions: search, use_exploit, generate_payload. Use only on systems you own or have explicit written permission to test.",
            parameters = mapOf(
                "action"   to ParameterSchema("string", "Action: search | use_exploit | generate_payload (default: search)", true),
                "query"    to ParameterSchema("string", "Search query for 'search' action, e.g. eternalblue", false),
                "module"   to ParameterSchema("string", "Module path for use_exploit, e.g. exploit/windows/smb/ms17_010_eternalblue", false),
                "rhost"    to ParameterSchema("string", "Remote host IP for use_exploit", false),
                "rport"    to ParameterSchema("integer", "Remote port for use_exploit (optional)", false),
                "lhost"    to ParameterSchema("string", "Local host IP for reverse shells", false),
                "lport"    to ParameterSchema("integer", "Local listening port for reverse shells (default: 4444)", false),
                "payload"  to ParameterSchema("string", "Payload for msfvenom, e.g. linux/x64/meterpreter/reverse_tcp", false),
                "output"   to ParameterSchema("string", "Output file for msfvenom payload (optional)", false),
                "format"   to ParameterSchema("string", "Output format for msfvenom: elf | exe | raw | python | bash | php | asp (default: elf)", false),
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val action  = args["action"]?.toString() ?: "search"
            val query   = args["query"]?.toString()
            val module  = args["module"]?.toString()
            val rhost   = args["rhost"]?.toString()
            val rport   = args["rport"]?.toString()
            val lhost   = args["lhost"]?.toString() ?: "0.0.0.0"
            val lport   = args["lport"]?.toString() ?: "4444"
            val payload = args["payload"]?.toString()
            val output  = args["output"]?.toString()
            val format  = args["format"]?.toString() ?: "elf"

            val command = when (action) {
                "search" -> {
                    if (query == null) return mapOf("success" to false, "error" to "query is required for search action")
                    "msfconsole -q -x \"search $query; exit\""
                }
                "use_exploit" -> {
                    if (module == null) return mapOf("success" to false, "error" to "module is required for use_exploit action")
                    if (rhost  == null) return mapOf("success" to false, "error" to "rhost is required for use_exploit action")
                    val rportLine   = if (rport   != null) "set RPORT $rport; " else ""
                    val payloadLine = if (payload != null) "set PAYLOAD $payload; " else ""
                    "msfconsole -q -x \"use $module; set RHOSTS $rhost; ${rportLine}${payloadLine}set LHOST $lhost; set LPORT $lport; run; exit\""
                }
                "generate_payload" -> {
                    if (payload == null) return mapOf("success" to false, "error" to "payload is required for generate_payload action")
                    val outFlag = if (output != null) "-o \"$output\"" else ""
                    "msfvenom -p $payload LHOST=$lhost LPORT=$lport -f $format $outFlag".trim()
                }
                else -> "msfconsole -q"
            }

            return mapOf(
                "success" to true,
                "tool" to "metasploit",
                "command" to command,
                "note" to "Run this command via execute_shell_command in the Linux sandbox."
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 14. HTTP Security Headers — فحص ترويسات الأمان HTTP
    // ─────────────────────────────────────────────────────────────────
    val httpHeadersTool = object : Tool {
        override val schema = ToolSchema(
            name = "check_http_headers",
            description = "Check HTTP security headers of a web server. Looks for: Content-Security-Policy, X-Frame-Options, X-XSS-Protection, Strict-Transport-Security, X-Content-Type-Options, Referrer-Policy, Permissions-Policy.",
            parameters = mapOf(
                "url"     to ParameterSchema("string", "Target URL, e.g. https://example.com/", true),
                "verbose" to ParameterSchema("boolean", "Show full response headers (default: false)", false),
                "follow_redirects" to ParameterSchema("boolean", "Follow HTTP redirects (default: true)", false),
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val url     = args["url"]?.toString() ?: return mapOf("success" to false, "error" to "url is required")
            val verbose = args["verbose"] as? Boolean ?: false
            val followR = args["follow_redirects"] as? Boolean ?: true

            val verboseFlag  = if (verbose) "-v" else "-I"
            val redirectFlag = if (followR) "-L" else ""
            val command = "curl $verboseFlag $redirectFlag -s -o /dev/null -D - \"$url\" | grep -iE 'content-security-policy|x-frame-options|x-xss-protection|strict-transport-security|x-content-type-options|referrer-policy|permissions-policy|server:|x-powered-by'".trim().replace(Regex("\\s+"), " ")

            return mapOf(
                "success" to true,
                "tool" to "curl_security_headers",
                "command" to command,
                "note" to "Run this command via execute_shell_command in the Linux sandbox."
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 15. SSL/TLS Scanner — فحص شهادات SSL وضعف التشفير
    // ─────────────────────────────────────────────────────────────────
    val sslScanTool = object : Tool {
        override val schema = ToolSchema(
            name = "ssl_scan",
            description = "SSL/TLS scanner: check certificate validity, supported cipher suites, protocol versions, and known vulnerabilities (POODLE, BEAST, HEARTBLEED, etc.). Tools: sslscan | testssl | openssl.",
            parameters = mapOf(
                "host"   to ParameterSchema("string", "Target hostname or IP", true),
                "port"   to ParameterSchema("integer", "Target port (default: 443)", false),
                "tool"   to ParameterSchema("string", "Tool to use: sslscan | testssl | openssl (default: sslscan)", false),
                "check_vulns" to ParameterSchema("boolean", "Check for known SSL/TLS vulnerabilities (testssl only, default: true)", false),
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val host       = args["host"]?.toString() ?: return mapOf("success" to false, "error" to "host is required")
            val port       = args["port"]?.toString() ?: "443"
            val tool       = args["tool"]?.toString() ?: "sslscan"
            val checkVulns = args["check_vulns"] as? Boolean ?: true

            val command = when (tool) {
                "sslscan"  -> "sslscan --no-colour $host:$port"
                "testssl"  -> {
                    val vulnFlag = if (checkVulns) "--vulnerable" else ""
                    "testssl.sh $vulnFlag $host:$port".trim()
                }
                "openssl"  -> "openssl s_client -connect $host:$port -showcerts < /dev/null 2>/dev/null | openssl x509 -noout -text"
                else       -> "sslscan $host:$port"
            }

            return mapOf(
                "success" to true,
                "tool" to "ssl_scan",
                "command" to command,
                "note" to "Run this command via execute_shell_command in the Linux sandbox."
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // ToolInfo definitions (for Settings → Tools tab display)
    // ─────────────────────────────────────────────────────────────────
    val nmapScanToolInfo = ToolInfo(
        id = "nmap_scan",
        name = "Nmap Network Scanner",
        description = "Discover hosts, open ports, and services on a network",
        nameRes = null,
        descriptionRes = null,
    )
    val sqlmapToolInfo = ToolInfo(
        id = "sqlmap_scan",
        name = "SQLmap SQL Injection Scanner",
        description = "Detect and exploit SQL injection vulnerabilities",
        nameRes = null,
        descriptionRes = null,
    )
    val gobusterToolInfo = ToolInfo(
        id = "gobuster_scan",
        name = "Gobuster Directory Scanner",
        description = "Brute-force directories, files, DNS subdomains, and virtual hosts",
        nameRes = null,
        descriptionRes = null,
    )
    val niktoToolInfo = ToolInfo(
        id = "nikto_scan",
        name = "Nikto Web Vulnerability Scanner",
        description = "Scan web servers for dangerous files, outdated software, and misconfigurations",
        nameRes = null,
        descriptionRes = null,
    )
    val hydraToolInfo = ToolInfo(
        id = "hydra_brute_force",
        name = "Hydra Brute Force Tool",
        description = "Fast network login cracker supporting SSH, FTP, HTTP, RDP, SMB, and more",
        nameRes = null,
        descriptionRes = null,
    )
    val hashcatToolInfo = ToolInfo(
        id = "hashcat_crack",
        name = "Hashcat Password Cracker",
        description = "GPU-accelerated password recovery supporting MD5, SHA, NTLM, bcrypt, and more",
        nameRes = null,
        descriptionRes = null,
    )
    val johnToolInfo = ToolInfo(
        id = "john_crack",
        name = "John the Ripper Password Cracker",
        description = "Crack password hashes using dictionary, single, or incremental modes",
        nameRes = null,
        descriptionRes = null,
    )
    val netcatToolInfo = ToolInfo(
        id = "netcat_tool",
        name = "Netcat Network Tool",
        description = "TCP/UDP Swiss Army Knife: connect, listen, port scan, banner grab, reverse shell",
        nameRes = null,
        descriptionRes = null,
    )
    val dnsReconToolInfo = ToolInfo(
        id = "dns_recon",
        name = "DNS Recon & WHOIS",
        description = "DNS enumeration, WHOIS lookup, and subdomain discovery",
        nameRes = null,
        descriptionRes = null,
    )
    val wpscanToolInfo = ToolInfo(
        id = "wpscan",
        name = "WPScan WordPress Scanner",
        description = "Enumerate WordPress plugins, themes, users, and known CVEs",
        nameRes = null,
        descriptionRes = null,
    )
    val enum4linuxToolInfo = ToolInfo(
        id = "enum4linux_scan",
        name = "Enum4linux SMB Enumerator",
        description = "Enumerate users, shares, groups, and policies from Windows/Samba systems",
        nameRes = null,
        descriptionRes = null,
    )
    val arpScanToolInfo = ToolInfo(
        id = "arp_scan",
        name = "ARP Scan Host Discovery",
        description = "Discover live hosts on a local network using ARP requests",
        nameRes = null,
        descriptionRes = null,
    )
    val metasploitToolInfo = ToolInfo(
        id = "metasploit_command",
        name = "Metasploit Framework",
        description = "Generate Metasploit commands for exploitation, post-exploitation, and payload generation",
        nameRes = null,
        descriptionRes = null,
    )
    val httpHeadersToolInfo = ToolInfo(
        id = "check_http_headers",
        name = "HTTP Security Headers Checker",
        description = "Check web server security headers: CSP, HSTS, X-Frame-Options, and more",
        nameRes = null,
        descriptionRes = null,
    )
    val sslScanToolInfo = ToolInfo(
        id = "ssl_scan",
        name = "SSL/TLS Scanner",
        description = "Check SSL/TLS certificate, cipher suites, and known vulnerabilities",
        nameRes = null,
        descriptionRes = null,
    )

    // ─────────────────────────────────────────────────────────────────
    // Aggregated lists
    // ─────────────────────────────────────────────────────────────────
    val hackingToolDefinitions = listOf(
        nmapScanToolInfo,
        sqlmapToolInfo,
        gobusterToolInfo,
        niktoToolInfo,
        hydraToolInfo,
        hashcatToolInfo,
        johnToolInfo,
        netcatToolInfo,
        dnsReconToolInfo,
        wpscanToolInfo,
        enum4linuxToolInfo,
        arpScanToolInfo,
        metasploitToolInfo,
        httpHeadersToolInfo,
        sslScanToolInfo,
    )

    val hackingTools = listOf(
        nmapScanTool,
        sqlmapTool,
        gobusterTool,
        niktoTool,
        hydraTool,
        hashcatTool,
        johnTool,
        netcatTool,
        dnsReconTool,
        wpscanTool,
        enum4linuxTool,
        arpScanTool,
        metasploitTool,
        httpHeadersTool,
        sslScanTool,
    )
}
