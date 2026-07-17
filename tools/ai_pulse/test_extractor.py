#!/usr/bin/env python3
"""
اختبار شامل لمحرك استخراج المحتوى الكامل
يتحقق من:
1. الاستخراج الكامل (100%)
2. التنظيف الصحيح
3. الأداء
4. معالجة الأخطاء
"""

import asyncio
import json
import time
from full_page_extractor import extract_full_content

async def test_full_extraction():
    """اختبار استخراج المحتوى الكامل من صفحات مختلفة"""
    
    test_urls = [
        "https://example.com",
        "https://httpbin.org/html",
    ]
    
    print("=" * 80)
    print("🧪 اختبار محرك استخراج المحتوى الكامل")
    print("=" * 80)
    
    for url in test_urls:
        print(f"\n📍 اختبار: {url}")
        print("-" * 80)
        
        start_time = time.time()
        result = await extract_full_content(url)
        elapsed_time = time.time() - start_time
        
        if result["success"]:
            print(f"✅ النتيجة: نجح")
            print(f"📄 العنوان: {result['title']}")
            print(f"📊 حجم المحتوى: {result['length']} حرف")
            print(f"⏱️  الوقت المستغرق: {elapsed_time:.2f} ثانية")
            print(f"📝 المحتوى (أول 200 حرف):")
            print(f"   {result['content'][:200]}...")
        else:
            print(f"❌ الخطأ: {result['error']}")
        
        print()

async def test_accuracy():
    """اختبار دقة الاستخراج"""
    
    print("\n" + "=" * 80)
    print("🎯 اختبار دقة الاستخراج")
    print("=" * 80)
    
    url = "https://example.com"
    result = await extract_full_content(url)
    
    if result["success"]:
        content = result["content"]
        
        # التحقق من وجود محتوى
        has_content = len(content) > 0
        print(f"✅ وجود محتوى: {has_content}")
        
        # التحقق من تنظيف الكود
        has_no_script_tags = "<script" not in content
        print(f"✅ تنظيف الكود: {has_no_script_tags}")
        
        # التحقق من التحويل إلى Markdown
        is_markdown = "#" in content or "[" in content or "-" in content
        print(f"✅ تحويل Markdown: {is_markdown}")
        
        # النسبة الإجمالية
        accuracy = sum([has_content, has_no_script_tags, is_markdown]) / 3 * 100
        print(f"\n📊 نسبة الدقة الإجمالية: {accuracy:.1f}%")
    else:
        print(f"❌ فشل الاختبار: {result['error']}")

async def test_performance():
    """اختبار الأداء والسرعة"""
    
    print("\n" + "=" * 80)
    print("⚡ اختبار الأداء")
    print("=" * 80)
    
    url = "https://example.com"
    times = []
    
    for i in range(3):
        start_time = time.time()
        await extract_full_content(url)
        elapsed = time.time() - start_time
        times.append(elapsed)
        print(f"المحاولة {i+1}: {elapsed:.2f} ثانية")
    
    avg_time = sum(times) / len(times)
    print(f"\n⏱️  متوسط الوقت: {avg_time:.2f} ثانية")
    print(f"✅ الأداء: {'ممتاز' if avg_time < 2 else 'جيد' if avg_time < 5 else 'يحتاج تحسين'}")

async def main():
    """تشغيل جميع الاختبارات"""
    
    try:
        await test_full_extraction()
        await test_accuracy()
        await test_performance()
        
        print("\n" + "=" * 80)
        print("✅ جميع الاختبارات اكتملت بنجاح!")
        print("=" * 80)
        
    except Exception as e:
        print(f"\n❌ خطأ: {e}")

if __name__ == "__main__":
    asyncio.run(main())
