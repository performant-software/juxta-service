/**
 * Javascript to support the heatmap visualization
 */
/*global $, document, Raphael, alert, window, location */

/**
 * Get the maximum, non-scrolling height for the heatmap content area
 */
var getMaxNonScrollHeight = function() {
   var maxH = $("#juxta-ws-content").parent().height();
   var extraH = $(".header").outerHeight() + $(".heatmap-toolbar").outerHeight();
   return (maxH - extraH );
};

/**
 * Create raphael canvases to use when drawing the change index circles
 */
var renderWitnessChangeIndexes = function() {
   var attribs = {
      stroke : '#000',
      'stroke-width' : 0.5,
      'stroke-linejoin' : 'round'
   };
   var colors = ["#FFFFFF", "#EFF0FF", "#D5D9FF", "#BBC1FF", "#A1AAFF", "#8792FF", "#6D7BFF", "#5363FF"];
   var dim = "#aaa";
   var dark = "#000";
   $(".change-index").each(function(index) {
      var htmlEle = document.getElementById($(this).attr("id"));
      var changeIndex = $(this).attr("juxta:change-index");
      $(this).attr("title", parseFloat(changeIndex).toFixed(2) + " change index from base text");
      var paper = new Raphael(htmlEle, $(this).width(), 22);
      $(this).data("paper", paper);
      var boxW = $(this).width() / 8 - 2;
      var x = 1;
      var i;
      for ( i = 0; i < 8; i+=1) {
         var box = paper.rect(x, 6, boxW, 10, 3);
         var currentLevel = i / 8.0;
         if (currentLevel <= changeIndex) {
            attribs.stroke = dark;
            attribs.fill = colors[i];
         } else {
            attribs.stroke = dim;
            attribs.fill = colors[0];
         }
         box.attr(attribs);
         x = x + boxW + 2;
      }
   });
};

/**
 * Purge the cached heatmap data and re-render the contents
 */
var refreshHeatmap = function () {

   // send an ajax DELETE to purge the heatmap cache for this set. Once
   // successful, just reload the page to get the updated view
   var setId = $('#setId').text();
   var csUrl = $('#ajax-base-url').text() + setId + $('#view-heatmap-segment').text();
   $.ajax({
      url : csUrl,
      type : 'DELETE',
      async : false,
      success : function() {
         location.reload();
      },
      error : function(jqXHR, textStatus, errorThrown) {
         alert("Unable refresh heatmap view:\n\n" + jqXHR.responseText);
      }
   });
};

/**
 * Show a note or margin box in the gutter, and align it with the specified top position
 */
var showAndAlign = function (top, tgtEle, tgtClass, ownerDiv) {
   tgtEle.width(ownerDiv.width() - 8);
   tgtEle.css({
      position : "relative",
      margin : 0,
      marginTop : 0,
      top : top
   });
   tgtEle.removeClass("hidden");

   var bot = tgtEle.position().top + tgtEle.outerHeight();
   if (bot > ownerDiv.height()) {
      ownerDiv.height(bot);
   }
};

/**
 * Layout notes boxes in the right-side gutter
 */
var layoutNotes = function() {

   var scrollPos = $(".heatmap-scroller").position().top;
   var scrollTop = $(".heatmap-scroller").scrollTop();
   var lastTop = -1;
   var totalHeight = 0;
   var top = 0;
   var firstTop = -1;

   // iterate over each note and find its anchor. align
   // the top of the note with the top of the anchor
   $(".note").each(function(index) {
      var noteId = $(this).attr("id");
      var anchorId = "note-anchor-" + noteId.substring(5);
      var anchor = $("#" + anchorId);

      if ( anchor.exists() ) {
         // the first note just gets positioned directly.
         if (lastTop === -1) {
            top = anchor.position().top - scrollPos + scrollTop;
         } else {
            // all others are positioned relateve and need to bump
            // their top pos by the accumulated height of all others
            var newTop = anchor.position().top - scrollPos + scrollTop;
            if (newTop <= (lastTop + totalHeight)) {
               // this overlaps the prior note. Just bump the top
               // 5 piles down (relative to the prior)
               top += 5;
            } else {
               top += (newTop - lastTop);
               top -= totalHeight;
            }
         }
   
         if (firstTop === -1) {
            firstTop = top;
         }
   
         showAndAlign(top, $(this), 'note', $("#note-boxes"));
         totalHeight += $(this).outerHeight();
         lastTop = top;
      }
   });

   // if the new layout pushes the height from non-scrolly
   // to scrolly, slap on the scroll bar and reduce the witdth
   // of all of the boxes a bit so the are not chopped off
   // by the newly reduced horizontal space.
   if (firstTop + totalHeight > getMaxNonScrollHeight()) {
      $("#heatmap-scroller").css("overflow-y", "visible");
      $(".note").width($(".note").width() - 10);
   }
};

/**
 * Toggle view of revisions 
 */
var toggleRevisionStyle = function() {
   var revTags = $(".rev");
   if ( $("#revisions-button").hasClass("pushed") === false ) {
      $("#revisions-button").addClass("pushed");
      revTags.removeClass("plain-revs");
      revTags.addClass("reveal");
   } else {
      $("#revisions-button").removeClass("pushed");
      revTags.removeClass("reveal");
      revTags.addClass("plain-revs");
   }
};

/**
 * Togggle view of page breaks
 */
var togglePbTags = function() {
   var pbTags = $(".page-break");
   if ( $("#pb-button").hasClass("pushed") === false ) {
      $("#pb-button").addClass("pushed");
      pbTags.removeClass("hidden");
   } else {
      $("#pb-button").removeClass("pushed");
      pbTags.addClass("hidden");
   }
};

/**
 * Toggle view of notes
 */
var toggleNotes = function() {
   var notes = $(".note-anchor");
   if ( $("#notes-button").hasClass("pushed") === false) {
      $("#notes-button").addClass("pushed");
      notes.removeClass("note-anchor-hidden");
      $("#note-boxes").show();
   } else {
      $("#notes-button").removeClass("pushed");
      notes.addClass("note-anchor-hidden");
      $("#note-boxes").hide();
   }
};

/**
 * Clear out all margin boxes and replace with note boxes
 */
var clearBoxes = function () {
   $("#margin-boxes").fadeOut(250, function() {
      var priorActive = $(".active");
      priorActive.removeClass("active");
      if ($("#notes-button").data("selected")) {
         $("#note-boxes").fadeIn(250);
         layoutNotes();
      }
   });
};

/**
 * Show all margin boxes for the specified alignment
 */
var showMarginBoxes = function (alignId) {

   // if we clicked same box, do nothing
   // otherwise clear the ould one
   var priorActive = $(".active");
   if (priorActive.length > 0) {
      var priorId = priorActive.attr("id");
      if (priorId === alignId) {
         return;
      } else {
         priorActive.removeClass("active");
      }
   }

   // FIRST turn on some dimming and a wait cursor
   $("#heatmap-text").trigger('diff-requested');
   $('#wait-popup').show();

   // hide ALL boxes. this to cover case when 1st click shows 4 and 2nd shows 1
   // must ensure that the extra 3 are no longer shown
   $('.margin-box').addClass('hidden');

   // make an ajax request to get json containing
   // data to fill out the margin boxes with detail
   var src = $("#" + alignId);
   var range = src.attr("juxta:range");
   var setId = $('#setId').text();
   var csUrl = $('#ajax-base-url').text() + setId + $('#fragment-segment').text();
   var url = csUrl + '?range=' + range + "&base=" + $("#baseId").text();

   $.ajax({
      contentType : 'application/json',
      accepts : 'application/json',
      type : 'GET',
      url : url,
      dataType : 'json',
      success : function(jsonData) {
         // toggle to margin box view
         $("#note-boxes").hide();
         $("#margin-boxes").show();

         // make clicked change active and show margin boxes
         src.addClass("active");

         // figure out the top pos of the clicked diff within the scroll div
         var scrollPos = $(".heatmap-scroller").position().top;
         var scrollTop = $(".heatmap-scroller").scrollTop();
         var boxTop = src.position().top - scrollPos + scrollTop;

         // fill boxes with change data
         var idx;
         for ( idx = 0; idx < jsonData.length; idx+=1) {
            var diff = jsonData[idx];
            var boxId = idx + 1;

            // set title with witness name
            var titleEle = $('#box-title-' + boxId);
            titleEle.html(diff.typeSymbol + diff.witnessName);

            var txtEle = $('#box-txt-' + boxId);
            txtEle.html(diff.fragment);

            showAndAlign(boxTop, $("#box-" + boxId), 'margin-box', $("#margin-boxes"));
            boxTop = boxTop + 5;
         }
         $('#wait-popup').hide();
         $("#heatmap-text").trigger('diff-request-complete');
      },
      error : function(jqXHR, textStatus, errorThrown) {
         alert("Unable to determine differences.\n     " + jqXHR.responseText);
         $('#wait-popup').hide();
         $("#heatmap-text").trigger('diff-request-complete');
      },
      xhrFields : {
         withCredentials : true
      }
   });
};

/**
 * Initialize heatmap size, layout and events
 */
var initializeHeatmap = function() {

   $("#juxta-ws-content").parent().css("overflow-y", "hidden");

   var h = getMaxNonScrollHeight();
   $(".heatmap-scroller").height(h);

   var fullHeight = $(".heatmap-text").outerHeight();
   if (fullHeight < h) {
      $(".heatmap-scroller").css("overflow-y", "hidden");
   }

   // initially show notes & pb
   $("#notes-button").addClass("pushed");
   $("#pb-button").addClass("pushed");

   // create a bunch of raphael canvases
   // for the non-base witnesses. Render the colored
   // blocks in them to indicate change index
   renderWitnessChangeIndexes();

   // space the note boxes in the margins
   layoutNotes();

   // clicks on background clear boxes
   // and revert to note display
   $("body").click(function() {
      clearBoxes();
   });

   $(".witness").on("click", function() {
      var witnessId = $(this).attr("id");
      // strip off 'witness-' and just get number
      var id = witnessId.substring(8);
      var setId = $('#setId').text();
      var csUrl = $('#ajax-base-url').text() + setId + $('#view-heatmap-segment').text() + "&base=" + id;
      window.location = csUrl;
   });

   // click heatmap to show margin boc
   $("#heatmap-text").on("click", ".heatmap", function(event) {
      showMarginBoxes($(this).attr("id"));
      event.stopPropagation();
   });

   $("#note-boxes").on("mouseenter", ".note", function(event) {
      var noteId = $(this).attr("id");
      $(this).addClass("highlighted");
      var anchorId = "note-anchor-" + noteId.substring(5);
      var anchorSpan = $("#" + anchorId);
      anchorSpan.removeClass("note-anchor");
      anchorSpan.addClass("note-anchor-highlighted");
   });

   $("#note-boxes").on("mouseleave", ".note", function(event) {
      var noteId = $(this).attr("id");
      $(this).removeClass("highlighted");
      var anchorId = "note-anchor-" + noteId.substring(5);
      var anchorSpan = $("#" + anchorId);
      anchorSpan.removeClass("note-anchor-highlighted");
      anchorSpan.addClass("note-anchor");
   });
};

$(window).resize(function() {
   // don't try to do anything if the curr page
   // does not contain a heatmap
   if ($(".heatmap-text").exists()) {

      var h = getMaxNonScrollHeight();
      $(".heatmap-scroller").height(h);
      var fullHeight = $(".heatmap-text").outerHeight();
      if (fullHeight < h) {
         $(".heatmap-scroller").css("overflow-y", "hidden");
      }

      // easiest to just clear boxes and reset notes on a resize
      clearBoxes();
      layoutNotes();
   }
});
