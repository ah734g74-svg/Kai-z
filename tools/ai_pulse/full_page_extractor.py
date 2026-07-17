import sys
import json
import asyncio
import httpx
from bs4 import BeautifulSoup
import markdownify

async def extract_full_content(url):
    """
    يستخرج المحتوى الكامل من الصفحة باستخدام استراتيجية هجينة:
    1. محاولة استخدام خدمات الاستخراج المتاحة.
    2. استخدام BeautifulSoup للتنظيف والتحويل إلى Markdown.
    """
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
    
    try:
        async with httpx.AsyncClient(timeout=30.0, follow_redirects=True) as client:
            response = await client.get(url, headers=headers)
            response.raise_for_status()
            html_content = response.text
            
            # تنظيف HTML
            soup = BeautifulSoup(html_content, 'html.parser')
            
            # إزالة العناصر غير الضرورية
            for element in soup(['script', 'style', 'nav', 'footer', 'header', 'aside', 'iframe', 'noscript']):
                element.decompose()
            
            # استخراج النص النظيف
            clean_html = str(soup)
            markdown_text = markdownify.markdownify(clean_html, heading_style="ATX")
            
            return {
                "success": True,
                "url": url,
                "title": soup.title.string if soup.title else "No Title",
                "content": markdown_text.strip(),
                "length": len(markdown_text)
            }
            
    except Exception as e:
        return {
            "success": False,
            "error": str(e)
        }

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "URL is required"}))
        sys.exit(1)
        
    url_to_extract = sys.argv[1]
    result = asyncio.run(extract_full_content(url_to_extract))
    print(json.dumps(result))
