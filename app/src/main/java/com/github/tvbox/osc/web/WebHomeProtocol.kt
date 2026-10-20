package com.github.tvbox.osc.web

/**
 * WebHome V1/V2 Bridge 协议常量与 JS 侧引导脚本。
 *
 * 协议约定（与 webhtv 原生实现保持一致）：
 *  1. 原生向 WebView 注入名为 [NATIVE_BRIDGE_NAME] 的 JavascriptInterface，
 *     仅暴露三个方法：
 *        invoke(requestId, method, payloadJson)   —— 发起调用（无返回值）
 *        resultLength(id)                         —— 大结果分片长度
 *        resultChunk(id, start)                   —— 大结果分片读取
 *  2. 页面侧由 [BOOTSTRAP_JS] 建立 window.fm 门面，把每次调用包成 Promise，
 *     并把 requestId 登记进 pending 表。
 *  3. 原生处理完毕后调用 window.[CALLBACK_HOST].resolve(requestId, payloadJson)
 *     或 window.[CALLBACK_HOST].reject(requestId, message, code)。
 *  4. 若结果超过 [INLINE_LIMIT]，原生把结果存入 results 表，
 *     仅回传 {"__fmResultId":"..."}，页面再用 resultChunk 分片拉取。
 */
object WebHomeProtocol {

    /** 原生注入到 window 上的 JavascriptInterface 名称。 */
    const val NATIVE_BRIDGE_NAME = "__fmNative"

    /** 页面侧回调宿主对象名（由 BOOTSTRAP_JS 创建）。 */
    const val CALLBACK_HOST = "__fmCallback"

    /** 结果内联阈值：超过此长度的结果走分片通道。 */
    const val INLINE_LIMIT = 12_000

    /** 分片大小。 */
    const val CHUNK_SIZE = 60_000

    /**
     * 页面侧引导脚本。必须在页面加载前（onPageStarted）注入，
     * 保证 DOMContentLoaded 时 window.fm 已可用。
     *
     * 暴露的方法签名严格对齐 eclipse.html 的调用方式：
     *   fm.vodHome(payload) -> Promise
     *   fm.vodCategory(typeId, page, options) -> Promise
     *   fm.openDetail(payload) -> Promise
     *   fm.vod(siteKey, vodId, title, pic, options) -> Promise
     *   fm.search(keyword, options) -> Promise
     *   fm.openSite() / fm.openVod() / fm.openSetting() -> Promise
     *   fm.res(url, options) -> String（同步）
     *   fm.ui.getViewport() -> Promise
     */
    val BOOTSTRAP_JS: String = """
        (function (w) {
          'use strict';
          if (w.fm && w.fm.__installed) return;

          var pending = {};
          var seq = 0;
          var native = w.$NATIVE_BRIDGE_NAME;

          function nextId() {
            seq += 1;
            return 'r' + Date.now().toString(36) + '_' + seq;
          }

          /* 大结果分片拼接 */
          function collect(id) {
            var total = native.resultLength(id);
            var out = '';
            var start = 0;
            while (start < total) {
              var chunk = native.resultChunk(id, start);
              if (!chunk) break;
              out += chunk;
              start += chunk.length;
            }
            try { native.clearResult(id); } catch (ignore) {}
            return out;
          }

          function parse(raw) {
            if (raw === null || typeof raw === 'undefined' || raw === '') return null;
            if (typeof raw === 'object') return raw;
            var text = String(raw);
            try { return JSON.parse(text); } catch (ignore) { return text; }
          }

          /* 页面侧回调宿主：原生通过 evaluateJavascript 调用这两个方法 */
          w.$CALLBACK_HOST = {
            resolve: function (requestId, payload) {
              var entry = pending[requestId];
              if (!entry) return;
              delete pending[requestId];
              var value = parse(payload);
              if (value && typeof value === 'object' && value.__fmResultId) {
                value = parse(collect(value.__fmResultId));
              }
              entry.resolve(value);
            },
            reject: function (requestId, message, code) {
              var entry = pending[requestId];
              if (!entry) return;
              delete pending[requestId];
              var error = new Error(message || 'WebHome 调用失败');
              error.code = code || '';
              entry.reject(error);
            }
          };

          /* 统一调用入口：登记 pending，交给原生异步处理 */
          function call(method, payload) {
            return new Promise(function (resolve, reject) {
              var requestId = nextId();
              pending[requestId] = { resolve: resolve, reject: reject };
              try {
                native.invoke(requestId, method, JSON.stringify(payload || {}));
              } catch (e) {
                delete pending[requestId];
                reject(e);
              }
            });
          }

          var fm = {
            __installed: true,

            /* ---- 内容读取 ---- */
            vodHome: function (payload) { return call('vod.home', payload || {}); },
            vodCategory: function (typeId, page, options) {
              var opts = options || {};
              return call('vod.category', {
                typeId: typeId,
                page: page || 1,
                filter: opts.filter !== false,
                extend: opts.extend || {}
              });
            },

            /* ---- 导航 ---- */
            openDetail: function (payload) { return call('navigation.openDetail', payload || {}); },
            vod: function (siteKey, vodId, title, pic, options) {
              var opts = options || {};
              return call('player.playVod', {
                siteKey: siteKey || '',
                vodId: vodId || '',
                title: title || '',
                pic: pic || '',
                content: opts.content || '',
                remarks: opts.remarks || ''
              });
            },
            search: function (keyword, options) {
              return call('app.search', { keyword: keyword || '', options: options || {} });
            },
            openSite: function () { return call('app.openSite', {}); },
            openVod: function () { return call('app.openVod', {}); },
            openSetting: function () { return call('app.openSetting', {}); },

            /* ---- 资源代理（同步） ---- */
            res: function (url, options) {
              try { return native.resourceUrl(String(url || ''), JSON.stringify(options || {})); }
              catch (ignore) { return url; }
            },

            /* ---- UI ---- */
            ui: {
              getViewport: function () { return call('ui.getViewport', {}); }
            },

            /* ---- 扩展 ---- */
            toast: function (message) { return call('ext.toast', { message: message || '' }); }
          };

          w.fm = fm;

          /* 通知页面 SDK 已就绪，eclipse.html 监听该事件后发起首次数据请求 */
          try { w.dispatchEvent(new Event('fmsdk')); } catch (ignore) {}
        })(window);
    """.trimIndent()

    /** 注入引导脚本时替换占位符。 */
    fun bootstrapScript(): String = BOOTSTRAP_JS
        .replace("\$NATIVE_BRIDGE_NAME", NATIVE_BRIDGE_NAME)
        .replace("\$CALLBACK_HOST", CALLBACK_HOST)
}
