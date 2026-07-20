/* Content script: injects main.js (GM shims + AO3 Translator) into page context.
 * The script tag approach ensures the code runs in the page's context,
 * just like Tampermonkey does, giving it access to the page's window object.
 */
(function() {
    'use strict';
    var runtime = (typeof browser !== 'undefined') ? browser.runtime :
                  (typeof chrome !== 'undefined') ? chrome.runtime : null;
    if (!runtime) {
        console.error('[AO3 Kiosk] No extension runtime found');
        return;
    }
    var script = document.createElement('script');
    script.src = runtime.getURL('main.js');
    script.onload = function() { script.remove(); };
    script.onerror = function(e) {
        console.error('[AO3 Kiosk] Failed to load main.js:', e);
    };
    (document.head || document.documentElement).appendChild(script);
})();
