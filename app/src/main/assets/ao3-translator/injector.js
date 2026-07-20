/* Content script: injects main.js (GM shims + AO3 Translator) into page context.
 * The script tag approach ensures the code runs in the page's context,
 * just like Tampermonkey does, giving it access to the page's window object.
 */
(function() {
    'use strict';
    var script = document.createElement('script');
    script.src = browser.runtime.getURL('main.js');
    script.onload = function() { script.remove(); };
    (document.head || document.documentElement).appendChild(script);
})();
