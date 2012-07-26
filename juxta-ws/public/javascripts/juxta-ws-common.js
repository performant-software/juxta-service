/**
 * Common helper functions used in other juxta-ws javascripts
 */

$.fn.exists = function () {
    return this.length !== 0;
}

function isEmbedded() {
    return $("#juxta-ws-content").exists();
}

function isHeatmap() {
    return $(".heatmap-text").exists();
}

function isSideBySide() {
    return $("#left-witness-text").exists();
}
