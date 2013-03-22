/*global $, document, Raphael, alert, window, location, setTimeout, setInterval, clearInterval */

// javascript for supporing side by side view
if (!window.Juxta) {
   window.Juxta = {};
}
if (!window.Juxta.SideBySide) {
   window.Juxta.SideBySide = {};
}

$(function() {

   var connectionSet = null;
   var connections = [];
   var paper = null;
   var hexDigits = ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f"];

   //Function to convert hex format to a rgb color
   function hex(x) {
      return isNaN(x) ? "00" : hexDigits[(x - x % 16) / 16] + hexDigits[x % 16];
   }

   function rgb2hex(rgb) {
      if ( rgb.indexOf("#") > -1 ) {
         return rgb;
      }
      rgb = rgb.match(/^rgb\((\d+),\s*(\d+),\s*(\d+)\)$/);
      return "#" + hex(rgb[1]) + hex(rgb[2]) + hex(rgb[3]);
   }

   window.Juxta.SideBySide.isLocked = function() {
      var locked = $("#scroll-mode-img").data("locked");
      return locked;
   };

   var alignOnDiff = function(diffEle) {
      // find the connection containing the diff clicked
      // and determine witch witness contains is (left or right)
      var side = "left";
      var clickedId = diffEle.attr("id");
      var conn = null;
      $.each(connections, function(i, testConn) {
         if (testConn.leftId === clickedId) {
            conn = testConn;
            side = "left";
            return false;
         } else if (testConn.rightId === clickedId) {
            conn = testConn;
            side = "right";
            return false;
         }
      });

      // if a matching connection was found, sync the views
      var delta, leftTop, rightTop;
      if (conn !== null) {
         if (side === "left") {
            $("#right-witness-text").data("action", "match");
            delta = (conn.right - conn.left);
            leftTop = $("#left-witness-text").scrollTop();
            $("#right-witness-text").scrollTop(leftTop + delta);
         } else {
            $("#left-witness-text").data("action", "match");
            delta = (conn.left - conn.right);
            rightTop = $("#right-witness-text").scrollTop();
            $("#left-witness-text").scrollTop(rightTop + delta);
         }
         renderConnections();
      }
   };

   var renderConnections = function() {
      if ($("#connections-div").exists() === false) {
         return;
      }

      // wipe out the old lines
      paper.clear();
      connectionSet.clear();

      // setup attributes and data
      var leftTop = $("#left-witness-text").scrollTop();
      var rightTop = $("#right-witness-text").scrollTop();
      var width = $("#connections-div").width();
      var height = $("#connections-div").height();
      var connAttribs = {
         'stroke' : rgb2hex($("#connector-prototype").css("color")),
         'stroke-width' : 0.75,
         'fill' : rgb2hex($("#connector-prototype").css("background-color"))
      };
      var moveLink = {
         stroke : rgb2hex($("#move-conn-prototype").css("color")),
         'stroke-width' : 0.5,
         'fill' : rgb2hex($("#move-conn-prototype").css("background-color"))
      };
      var litConnAttribs = {
         'stroke' : rgb2hex($("#lit-connector-prototype").css("color")),
         'stroke-width' : 0.5,
         'fill' : rgb2hex($("#lit-connector-prototype").css("background-color"))
      };

      // run thru each connection and draw a line from left to right
      var pathStr, line;
      var moveLines = [];
      var litLine = null;
      $.each(connections, function(i, conn) {
         if (conn.type === "move-hidden") {
            return true;
         }

         // grab co-ordinates of the connection and offset them by the
         // current scroll pos. This puts them relative to the visible
         // gutter area
         var lt = conn.leftTop - leftTop;
         var lb = lt + conn.leftHeight;
         var rt = conn.rightTop - rightTop;
         var rb = rt + conn.rightHeight;
         pathStr = "M0," + lt + "L" + width + "," + rt + "L" + width + "," + rb + "L0," + lb + "Z";

         if (conn.type === "move") {
            line = paper.path(pathStr);
            line.attr(moveLink);
            moveLines.push(line);
            connectionSet.push(line);
            conn.line = line;
            return true;
         }

         // if either is  out of the visible area, there is nothing
         // more that can be rendered - stop.
         if (lt > height || rt > height ) {
            return false;
         }

         // if either occurs befor the visible area skip them
         if (lb < 0 || rb < 0 ) {
            return true;
         }

         line = paper.path(pathStr);
         connectionSet.push(line);
         if (conn.lit) {
            line.attr(litConnAttribs);
            litLine = line;
         } else {
            line.attr(connAttribs);
            line.toBack();
         }
         conn.line = line;
      });

      $.each(moveLines, function(i, mv) {
         mv.toBack();
      });
      
      if ( litLine != null ) {
         litLine.toFront();
      }

      connectionSet.mouseover(function() {
         this.attr(litConnAttribs);
         var foo = this;
         $.each(connections, function(i, conn) {
            if (conn.line == foo) {
               foo.toFront();
               var ele = $("#" + conn.leftId);
               if (ele.hasClass() === false) {
                  ele.addClass("lit");
               }
               if (ele.width() === 0) {
                  ele.html("&nbsp;&nbsp;");
               }
               ele = $("#" + conn.rightId);
               if (ele.hasClass() === false) {
                  ele.addClass("lit");
               }
               if (ele.width() === 0) {
                  ele.html("&nbsp;&nbsp;");
               }
            }
         });
      });
      connectionSet.mouseout(function() {
         this.attr(connAttribs);
         var foo = this;
         $.each(connections, function(i, conn) {
            if (conn.line == foo) {
               var ele = $("#" + conn.leftId);
               ele.removeClass("lit");
               if (ele.html() === "&nbsp;&nbsp;") {
                  ele.html("");
               }
               ele = $("#" + conn.rightId);
               ele.removeClass("lit");
               if (ele.html() === "&nbsp;&nbsp;") {
                  ele.html("");
               }
               $("#" + conn.rightId).removeClass("lit");
            }
         });
      });
      connectionSet.click(function() {
         var foo = this;
         $.each(connections, function(i, conn) {
            if (conn.line == foo) {
               alignOnDiff($("#" + conn.rightId));
            }
         });
      });
   };

   var highlightDiff = function(diffEle) {
      diffEle.addClass("lit");
      var conn = diffEle.attr("juxta:connect-to");
      $("#diff-" + conn).addClass("lit");
      if ($("#diff-" + conn).width() === 0) {
         $("#diff-" + conn).html("&nbsp;&nbsp;");
      }

      var diffId = diffEle.attr("id");
      var i = 0;
      $.each(connections, function(i, conn) {
         if (conn.rightId === diffId || conn.leftId === diffId) {
            conn.lit = true;
            return false;
         }
      });
      renderConnections();
   };

   var clearDiffHighlight = function() {
      $(".lit").each(function(index) {
         if ($(this).html() === "&nbsp;&nbsp;") {
            $(this).html("");
         }
      });
      $(".diff").removeClass("lit");
      $.each(connections, function(i, conn) {
         conn.lit = false;
      });
      renderConnections();
   };

   /**
    * Render the left and right witness diff brackets in the narrow gutter
    * divs on either side of the connections area
    */
   var calculateAlignments = function( fireEvent ) {
      // grab some info needed to calculate positions of brackets
      var hdrHeight = $(".header").outerHeight(true);
      var fntSize = $('.witness-text').css('font-size');
      var defaultHeight = parseInt(fntSize, 10)+2;
      var rightScrollTop = $("#right-witness-text").scrollTop();
      var leftScrollTop = $("#left-witness-text").scrollTop();
      connections = [];

      $("body").trigger('wait-requested', ["Calculating differences..."]);
      $('#wait-popup').show();

      var diffs = $("#right-witness-text").find(".diff");
      $("#right-witness-text").find(".move").each(function() {
         diffs.push($(this));
      });

      var idx = 0;
      var chunkSize = 500;
      var thisChunk = 0;
      var done = false;
      var rightId, rightDiffTop, rightDiffHeight, rightDiffBottom;
      var connectToId, connType;
      var leftId, leftDiffTop, leftDiffHeight, leftDiffBottom;
      $("#toggle-moves").hide();

      // fake thread... run thru chunks of diffeences on an interval
      var tid = setInterval(function() {
         thisChunk = chunkSize;
         if (idx + thisChunk > diffs.length) {
            thisChunk = diffs.length - idx;
            clearInterval(tid);
            done = true;
         }

         $.each(diffs.slice(idx, (idx + thisChunk)), function(i, diff) {

            // track type of connection
            connType = "diff";
            if ($(diff).hasClass("move")) {
               connType = "move";
               $("#toggle-moves").show();
            }

            // calculate the extents of the RIGHT witness diff
            rightDiffTop = $(diff).position().top - hdrHeight + rightScrollTop;
            rightDiffHeight = $(diff).height();
            if (rightDiffHeight === 0) {
               rightDiffHeight = defaultHeight;
            }
            rightDiffBottom = rightDiffTop + rightDiffHeight;
            rightId = $(diff).attr("id");

            // Get the LEFT witness diff
            connectToId = connType + "-" + $(diff).attr("juxta:connect-to");
            if ($("#" + connectToId).exists()) {
               leftDiffTop = $("#" + connectToId).position().top - hdrHeight + leftScrollTop;
               leftDiffHeight = $("#" + connectToId).height();
               if (leftDiffHeight === 0) {
                  leftDiffHeight = defaultHeight;
               }
               leftDiffBottom = leftDiffTop + leftDiffHeight;
               leftId = $("#" + connectToId).attr("id");

               connections.push({
                  leftTop : leftDiffTop,
                  left : leftDiffTop + leftDiffHeight / 2,
                  leftHeight : leftDiffHeight,
                  leftId : leftId,
                  rightTop : rightDiffTop,
                  right : rightDiffTop + rightDiffHeight / 2,
                  rightHeight : rightDiffHeight,
                  rightId : rightId,
                  lit : false,
                  type : connType
               });
            }
            idx += 1;
         });

         if (done === true) {
            // sort connections in ascending order by right pos
            connections.sort(function(a, b) {
               if (a.right < b.right) {
                  return -1;
               }
               if (a.right > b.right) {
                  return 1;
               }
               return 0;
            });

            renderConnections();
            $('#wait-popup').hide();
            $("body").trigger('wait-completed');
            if ( fireEvent) {
               $("body").trigger('sbs-alignments-calculated');
            }
         }
      }, 50);
   };

   /**
    * Size the line bracket gutters and connection canvas to
    * match available area. Positions them appropriately
    */
   var initCanvas = function() {
      if ($("#left-witness").exists() === false) {
         return;
      }
      // figure out the gap between the text areas
      var top = $("#left-witness").position().top;
      var height = $("#left-witness").height();
      var left = $("#left-witness").position().left + $("#left-witness").outerWidth(true);
      var width = $("#right-witness").position().left - left;

      var imgLeft = left + (width - 32) / 2;
      $("#scroll-mode-img").css({
         "top" : (top + 1) + "px",
         "left" : (imgLeft) + "px"
      });

      // get height headers
      var hdrHeight = $("#left-witness .header").outerHeight(true);
      top += hdrHeight;
      height -= hdrHeight;
      var hT = $("#left-witness-text").offset().top;

      // position the center connections paper
      $("#connections-div").css({
         "top" : (hT) + "px",
         "left" : left + "px"
      });
      $("#connections-div").width(width);
      $("#connections-div").height(height);

      // create papers to match size
      paper = new Raphael($("#connections-div")[0], width, height);
      connectionSet = paper.set();
   };

   /**
    * Size left and right witness text to fix within screen bounds.
    * Enable scoll overflow of each div as necessary
    */
   var initDocumentHeight = function() {
      var newH = 0;
      var maxH, extraH;
      if ($("#embedded").text() === "false") {
         $(".content-scroller").css("overflow-y", "hidden");
         maxH = $(".content-scroller").height();
         extraH = $(".header").outerHeight(true);
         newH = maxH - extraH;
      } else {
         var parent = $("#juxta-ws-content").parent();
         extraH = $(".header").outerHeight(true);
         parent.css("overflow-y", "hidden");
         newH = parent.height() - extraH;
      }

      $(".witness-text").height(newH);
      $(".witness-text").css("overflow-y", "auto");

      // when in locked mode, only show the RIGHT scrollbar
      if (window.Juxta.SideBySide.isLocked()) {
         $("#left-witness-text").css("overflow-y", "hidden");
      }
   };

   var handleLockToggle = function() {
      // grab the current image and strip off the file name.
      // it will be added back in based on the new state of
      // the lock flag
      var imgSrc = $("#scroll-mode-img").attr("src");
      var pos = imgSrc.lastIndexOf("/");
      imgSrc = imgSrc.substring(0, pos);
      var isLocked = $("#scroll-mode-img").data("locked");
      isLocked = !isLocked;
            
      $("#right-witness-text").data("action","lock-change");
      $("#left-witness-text").data("action","lock-change");
      
      $("#scroll-mode-img").data("locked", isLocked);

      // grab current scroll pos and convert to percent
      var top = $("#left-witness-text").scrollTop();
      var full = $("#left-witness-text")[0].scrollHeight;
      var percent = top / full;
      
      if (isLocked === true) {
         $("#scroll-mode-img").attr("src", imgSrc + "/lock.gif");
         $("#left-witness-text").css("overflow-y", "hidden");
      } else {
         $("#scroll-mode-img").attr("src", imgSrc + "/unlock.gif");
         $("#left-witness-text").css("overflow-y", "auto");
      }
              
      // reset the scroll position to match the one calculated above
      full = $("#left-witness-text")[0].scrollHeight;
      top = full*percent;
      $("#left-witness-text").scrollTop( top);

      setTimeout(function() {
         initDocumentHeight();
         initCanvas();
         calculateAlignments(false);
         renderConnections();
      }, 500);

   };

   /*
    * find a connection given a position in either the right or left witness
    */
   var findConnection = function(tgtPos, side) {
      var out = null;
      $.each(connections, function(i, conn) {
         if (side === "right") {
            if (conn.right > tgtPos) {
               out = conn;
               return false;
            }
         } else {
            if (conn.left > tgtPos) {
               out = conn;
               return false;
            }
         }
      });

      if (out === null) {
         return connections[connections.length - 1];
      } else {
         return out;
      }
   };

   /**
    * Sync the RIGHT text to the LEFT. This is a special case caused when
    * browser searches jump the left text to a new location.
    */
   var syncLeftJump = function() {
      // be sure to flag this scroll as the result of a JUMP
      $("#right-witness-text").data("action", "jump");
      var leftTop = $("#left-witness-text").scrollTop();

      // Special case: no diffs. Just sync 1 to 1
      if (connections.length === 0) {
         $("#right-witness-text").scrollTop(leftTop);
      } else {
         var maxLeftHeight = $("#left-witness-text")[0].scrollHeight;
         var leftDivHeight = $("#left-witness-text").outerHeight(true);

         // pick a suitable midpoint in the left text to sync to.
         // if this new jump position is near the start or end, pick
         // a point at start or end respectively
         var targetDivPercent = 0.5;
         if ((leftTop + leftDivHeight) > maxLeftHeight * 0.9) {
            targetDivPercent = 0.9;
         } else if (leftTop < maxLeftHeight * 0.1) {
            targetDivPercent = 0.1;
         }

         // find nearest connection relative to this point in left witness
         var conn = findConnection(leftTop + leftDivHeight * targetDivPercent, "left");
         var delta = (conn.right - conn.left);

         // sync exactly to this point
         $("#right-witness-text").scrollTop(leftTop + delta);
      }

      // re-render al connection lines
      renderConnections();
   };
   
   var showMorePanel = function( show ) {
      var h = $("#left-witness-text").height(); 
      if ( show ) {
         if ( $("div.more-scroller").is(":visible") === false ) {
            $("div.more-scroller").show();
            $("#left-witness-text").height( h-$("div.more-scroller").outerHeight(true));
         }
      } else {
         if ( $("div.more-scroller").is(":visible") ) {
            $("div.more-scroller").hide();
            $("#left-witness-text").height( h+$("div.more-scroller").outerHeight(true));
         }   
      }
   };

   var syncScrolling = function() {

      // Flag this as sync scroll event and grab the new top of the right text
      $("#left-witness-text").data("action", "sync");
      var rightTop = $("#right-witness-text").scrollTop();
      var lastRight = $("#right-witness-text").data("lastTop");
      var leftTop = $("#left-witness-text").scrollTop();

      // Special case: no diffs. Just sync 1 to 1
      if (connections.length === 0) {
         $("#left-witness-text").scrollTop(rightTop);
      } else {
         var scrollDelta = rightTop - lastRight;
         var maxRightHeight = $("#right-witness-text")[0].scrollHeight;
         var rightDivHeight = $("#right-witness-text").outerHeight(true);

         // grab coordinates of the connection and offset them by the
         // current scroll pos. This puts them relative to the visible
         // gutter area. Offset the by a percentage of the overall height to
         // keep the aligned lines closer to the middle of the display.
         // SPECIAL: When scrolling down and nearing the end of the document,
         // look further ahead to pick up the last few diffs that would be missed if we
         // only looked 50% down the screen. Without this, one of the witnesses
         // may be chopped off at the end. Same deal for scrolling up.
         var targetDivPercent = 0.5;
         if (scrollDelta > 0) {
            if ((rightTop + rightDivHeight) > maxRightHeight * 0.9) {
               targetDivPercent = 0.9;
               if (rightTop + rightDivHeight >= (maxRightHeight - 10)) {
                  targetDivPercent = 1.0;
               }
            }
         } else {
            if (rightTop < maxRightHeight * 0.1) {
               targetDivPercent = 0.1;
               if (rightTop < 10) {
                  targetDivPercent = 0.0;
               }
            }
         }

         // find the nearest connection and determine the difference between
         // right and left text positions at this point
         var conn = findConnection(rightTop + rightDivHeight * targetDivPercent, "right");
         var leftPos = conn.left - leftTop;
         var rightPos = conn.right - rightTop;
         var posDelta = (leftPos - rightPos);

         if (scrollDelta > 0) {
            // Scrolling from top to bottom
            if (leftPos > rightPos) {
               // Until we are at the last 10% of the witness,
               // gradually catch up diffs for a smooter scroll.
               // At the end, make big jumps to keep the last diffs
               // sync'd up and full content on screen.
               if ((rightTop + rightDivHeight) < maxRightHeight * 0.85) {
                  posDelta = posDelta * 0.5;
               }
            } else if (rightPos > leftPos) {
               posDelta = 0;
            }
         } else {
            // scrolling from bottom to top
            if (leftPos > rightPos) {
               posDelta = 0;
            } else if (rightPos > leftPos) {
               // same as above; make gradual adjustments
               // until we near the top. Then make bigger jumps.
               if (rightTop > maxRightHeight * 0.1) {
                  posDelta = posDelta * 0.5;
               }
            }
         }
         
         var moreVisible = false;
         if ( (rightTop+rightDivHeight) == maxRightHeight ) {
            var maxL = $("#left-witness-text")[0].scrollHeight;
            var heightL = $("#left-witness-text").outerHeight(true);
            var topL = $("#left-witness-text").scrollTop();
            if ( (topL+heightL) < (maxL*0.95) ) {
               moreVisible = true;
            }
         }
         showMorePanel(moreVisible);


         // do the scroll on left to synch it with right
         $("#left-witness-text").scrollTop(leftTop + posDelta);
      }

      // save this top as prior so deltas can be calculated
      $("#right-witness-text").data("lastTop", rightTop);

      // re-render the connecting lines
      renderConnections();
   };

   var changeWitness = function(sourceButtonId) {

      var witnessId = $("#left-witness-id").text();
      $("#side").text("left");
      if (sourceButtonId.indexOf("right") !== -1) {
         witnessId = $("#right-witness-id").text();
         $("#side").text("right");
      }

      $(".witness-option").removeClass("sbs-wit-hover");
      $(".witness-option").removeClass("sbs-wit-selected");
      $(".witness-option").each(function(index) {
         var id = $(this).attr("id").substring("sel-sbs-wit-".length);
         if (id === witnessId) {
            $(this).addClass("sbs-wit-selected");
         }
      });

      var btn = $("#" + sourceButtonId);
      var top = btn.offset().top + btn.outerHeight(true) + 5;
      var left = btn.offset().left + btn.outerWidth(true) - $('.witnesses-popup').outerWidth(true);
      $('.witnesses-popup').css({
         "top" : top + "px",
         "left" : left + "px"
      });
      $('.witnesses-popup').show();
   };

   var cancelWitnessChange = function() {
      $('.witnesses-popup').hide();
   };

   var okWitnessChange = function() {

      // find the selected ID from the list
      var selectedId = "";
      $(".witness-option").each(function(index) {
         if ($(this).hasClass("sbs-wit-selected")) {
            selectedId = $(this).attr("id").substring("sel-sbs-wit-".length);
         }
      });
      $('.witnesses-popup').hide();

      // determine the two witness Ids using the knowledge
      // of which id was just changed to find the other
      var leftId = "";
      var rightId = "";
      if ($("#side").text() === "left") {
         leftId = selectedId;
         rightId = $("#right-witness-id").text();
      } else {
         leftId = $("#left-witness-id").text();
         rightId = selectedId;
      }

      // request the new view
      var url = document.URL;
      var docStart = url.indexOf('&docs');
      var docEnd = url.indexOf('&', docStart + 1);
      if (docEnd === -1) {
         url = url.substring(0, docStart) + "&docs=" + leftId + "," + rightId;
      } else {
         url = url.substring(0, docStart) + "&docs=" + leftId + "," + rightId + url.substring(docEnd);
      }
      window.location = url;
   };

   var initMouseWheelScrollHandling = function() {

      $("#left-witness-text").bind("mousewheel", function(event, delta) {
         event.preventDefault();

         // when locked and moving wheel over the left witness,
         // just transfer the scroll to the RIGHT text and sync the two
         var currTop = 0;
         if (window.Juxta.SideBySide.isLocked()) {
            $("#right-witness-text").data("action", "wheel");
            currTop = $("#right-witness-text").scrollTop();
            $("#right-witness-text").scrollTop(currTop -= (delta * 30));
            syncScrolling();
         } else {
            currTop = $("#left-witness-text").scrollTop();
            $("#left-witness-text").scrollTop(currTop -= (delta * 30));
         }
      });

      $("#right-witness-text").bind("mousewheel", function(event, delta) {
         event.preventDefault();

         // always scroll the text to match wheel delta
         $("#right-witness-text").data("action", "wheel");
         var currTop = $("#right-witness-text").scrollTop();
         $("#right-witness-text").scrollTop(currTop -= (delta * 30));

         if (window.Juxta.SideBySide.isLocked()) {
            // in locked mode sync the right and left texts
            syncScrolling();
         } else {
            // Unlocked mode, just redraw connections
            renderConnections();
         }
      });
   };

   var initWitnessScrollHandling = function() {

      $("#left-witness-text").scroll(function(eventObject) {

         if (window.Juxta.SideBySide.isLocked() === false) {
            // just update the connecting lines
            renderConnections();
         } else {
            // The syncScrolling method will update the positions of the
            // left text, resulting in this event being triggered. In this
            // case, there will be data appened to the text to distinguish
            /// this as a sync'd scroll. Grab this data and immediatey reset it.
            var data = $("#left-witness-text").data("action");
            $("#left-witness-text").data("action", "none");

            // See if this is from a sync. If it is, there is
            // nothing more to do. If not, this is caused by something
            // that made the left text jump to a new scroll location - usually
            // a search. Manually sync the right text to match up with
            /// this new position.
            if (data === "none") {
               syncLeftJump();
            }
         }
      });

      $("#right-witness-text").scroll(function(eventObject) {

         // right drives the sync sync. Handle this here.
         if (window.Juxta.SideBySide.isLocked()) {
            var data = $("#right-witness-text").data("action");
            $("#right-witness-text").data("action", "none");
            if (data === "none") {
               syncScrolling();
            }
         } else {
            renderConnections();
         }
      });
   };

   /**
    * Sync documents to a percentage position in the LEFT document
    */
   window.Juxta.SideBySide.syncDocuments = function( scrollPercent ) {
      // just scrolling here will trigger the scroll handler and auto sync the docs
      var full = $("#left-witness-text")[0].scrollHeight;
      $("#left-witness-text").scrollTop( scrollPercent * full );
   };

   /**
    * Main entry point for visualization: initialize everythign anf make it ready for use
    */
   var scrollTimer = -1;
   window.Juxta.SideBySide.initialize = function() {
      $("body").css("overflow","hide");
      $("#juxta-ws-content").css("overflow", "hide");
      $("#left-witness-text").data("action", "none");
      $("#right-witness-text").data("action", "none");
      $("#side-by-side").data("isResizing", false);
      $("#right-witness-text").data("lastTop", 0);
      
      // special scroll helper
      $("div.more-scroller").on("mousedown", function() {
         if ( scrollTimer === -1 ) {
            scrollTimer = setInterval(function() {
               var t = $("#left-witness-text").scrollTop();
               $("#left-witness-text").scrollTop(t+20);   
            }, 100);
         }
      });
      $("div.more-scroller").on("mouseup mouseout", function() {
         if ( scrollTimer !== -1 ) {
            clearInterval(scrollTimer);
            scrollTimer = -1;
         } 
      });

      // initially, scrolling is LOCKED. Must be set before
      // the calls to init height/canvas
      $("#scroll-mode-img").data("locked", true);

      // Setup click handling that allows diffs to auto-align when clicked
      $(".witness-text").on("click", ".diff", function(event) {
         alignOnDiff($(this));
      });
      $(".witness-text").on("click", ".move", function(event) {
         alignOnDiff($(this));
      });

      $(".witness-text").on("mouseenter", ".diff", function(event) {
         highlightDiff($(this));
      });

      $(".witness-text").on("mouseleave", ".diff", function(event) {
         clearDiffHighlight();
      });

      $("#scroll-mode-img").click(function() {
         handleLockToggle();
      });

      // event handling
      $("#change-left").on("click", function() {
         changeWitness('change-left');
      });
      $("#change-right").on("click", function() {
         changeWitness('change-right');
      });
      $("#sbs-ok-button").on("click", function() {
         okWitnessChange();
      });
      $("#sbs-cancel-button").on("click", function() {
         cancelWitnessChange();
      });
      $(".witness-option").on("mouseenter", function() {
         if ($(this).hasClass("sbs-wit-hover") === false && $(this).hasClass("sbs-wit-selected") === false) {
            $(this).addClass("sbs-wit-hover");
         }
      });
      $(".witness-option").on("mouseleave", function() {
         $(this).removeClass("sbs-wit-hover");
      });
      $(".witness-option").on("click", function() {
         $(".witness-option").removeClass("sbs-wit-selected");
         $(this).addClass("sbs-wit-selected");
      });
      
      $("#toggle-moves").on("click", function() {
         var isPushed = $(this).hasClass("pushed");
         $.each(connections, function(idx, conn) {
            if ( isPushed ) {
                if (conn.type === "move") {
                   conn.type = "move-hidden";
                }
            } else {
               if (conn.type === "move-hidden") {
                   conn.type = "move";
                }
            }
         });  
         if ( isPushed ) {
            $(this).removeClass("pushed");
            $("#side-by-side").find(".move").each( function() {
               $(this).removeClass("move");
               $(this).addClass("move-hidden");
            });
         } else {
            $(this).addClass("pushed");
            $("#side-by-side").find(".move-hidden").each( function() {
               $(this).removeClass("move-hidde");
               $(this).addClass("move");
            });
         }
         renderConnections();
      });
   };
   
   $(window).load(function () {
      if (  $("#left-witness-text").exists()  ) {
         setTimeout( function() {
            $("body").trigger('sidebyside-loaded');
            initDocumentHeight();
            initCanvas();
            calculateAlignments(true);
            initMouseWheelScrollHandling();
            initWitnessScrollHandling();
         }, 500);
      } else { 
         $("body").trigger('sidebyside-loaded');
      }
   });

   var rtime = null;
   var resizing = false;
   var resizeDelta = 500;
   var doneResizing = function() {
      if (new Date() - rtime < resizeDelta) {
         setTimeout(doneResizing, resizeDelta);
      } else {
         resizing = false;

         $(".canvas-div").show();
         initDocumentHeight();
         initCanvas();
         calculateAlignments(false);
      }
   };

   $(window).resize(function() {
      rtime = new Date();
      if (resizing === false) {
         resizing = true;
         if (paper != null) {
            paper.clear();
         }
         $(".canvas-div").hide();
         setTimeout(doneResizing, resizeDelta);
      }
   });
});

