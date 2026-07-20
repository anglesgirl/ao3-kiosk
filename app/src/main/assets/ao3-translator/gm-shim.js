/* GM_ API shims for running AO3 Translator without Tampermonkey.
 * Provides localStorage-backed storage and fetch-based HTTP requests.
 */

var GM_info = {
    scriptHandler: 'AO3 Kiosk',
    script: {
        name: 'AO3 Translator',
        version: '1.9.0',
        description: 'AO3 Chinese localization'
    },
    version: '1.9.0'
};

function GM_getValue(key, defaultValue) {
    try {
        var value = localStorage.getItem(key);
        if (value === null) return defaultValue;
        return JSON.parse(value);
    } catch (e) {
        return defaultValue;
    }
}

function GM_setValue(key, value) {
    try {
        localStorage.setItem(key, JSON.stringify(value));
    } catch (e) {}
}

function GM_deleteValue(key) {
    try {
        localStorage.removeItem(key);
    } catch (e) {}
}

function GM_listValues() {
    var keys = [];
    try {
        for (var i = 0; i < localStorage.length; i++) {
            keys.push(localStorage.key(i));
        }
    } catch (e) {}
    return keys;
}

function GM_addStyle(css) {
    var style = document.createElement('style');
    style.textContent = css;
    (document.head || document.documentElement).appendChild(style);
    return style;
}

function GM_xmlhttpRequest(details) {
    var init = {
        method: details.method || 'GET',
        headers: details.headers || {},
        credentials: 'include'
    };
    if (details.data) init.body = details.data;
    if (details.timeout) init.signal = AbortSignal.timeout(details.timeout);

    fetch(details.url, init).then(function(response) {
        return response.text().then(function(text) {
            var responseHeaders = '';
            response.headers.forEach(function(v, k) {
                responseHeaders += k + ': ' + v + '\r\n';
            });
            if (details.onload) {
                details.onload({
                    status: response.status,
                    statusText: response.statusText,
                    responseText: text,
                    response: text,
                    responseHeaders: responseHeaders,
                    finalUrl: response.url,
                    responseURL: response.url
                });
            }
        });
    }).catch(function(error) {
        if (details.onerror) {
            details.onerror({ error: error.message });
        } else if (details.ontimeout) {
            details.ontimeout({ error: error.message });
        }
    });
}

function GM_registerMenuCommand(name, fn, accessKey) {
    return 0;
}

function GM_unregisterMenuCommand(id) {}

function GM_notification(details, ondone) {
    if (ondone) ondone();
}

function GM_getResourceURL(name) {
    var resources = {
        'vIcon': 'https://cdn.jsdelivr.net/gh/V-Lipset/ao3-chinese@main/assets/icon.png',
        'santaHat': 'https://cdn.jsdelivr.net/gh/V-Lipset/ao3-chinese@main/assets/santa%20hat.png'
    };
    return resources[name] || '';
}

function GM_getResourceText(name) {
    return '';
}

function GM_addValueChangeListener(key, listener) {
    return 0;
}

function GM_removeValueChangeListener(id) {}

function GM_download(details) {}

function GM_setClipboard(text) {
    try {
        navigator.clipboard.writeText(text);
    } catch (e) {}
}

var unsafeWindow = window;

