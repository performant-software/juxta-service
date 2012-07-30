/*global $, document, Raphael, alert, window, location, setTimeout, isEmbedded */

// javascript for supporing side by side view
if (!window.Juxta) {
   window.Juxta = {};
}
if (!window.Juxta.SideBySide) {
   window.Juxta.SideBySide = {};
}

$(function() {

   window.Juxta.SideBySide.isLocked = function() {
      var locked = $("#scroll-mode-img").data("locked");
      return locked;
   };

   var getMaxWitnessHeight = function() {
      return $("#side-by-side").data("maxWitnessHeight");
   };

   var renderConnections = function() {
      // wipe out the old lines
      var connectionPaper = $("#connections-div").data("paper");
      connectionPaper.clear();

      // setup attributes and data
      var leftTop = $("#left-gutter-div").scrollTop();
      var rightTop = $("#right-gutter-div").scrollTop();
      var width = $("#connections-div").width();
      var height = $("#connections-div").height();
      var connAttribs = {
         stroke : '#050',
         'stroke-width' : 0.5,
         'stroke-dasharray' : '- '
      };
      var faded1 = {
         stroke : '#8a8',
         'stroke-width' : 0.5,
         'stroke-dasharray' : '- '
      };
      var faded2 = {
         stroke : '#beb',
         'stroke-width' : 0.5,
         'stroke-dasharray' : '- '
      };
      var moveLink = {
         stroke : '#f00',
         'stroke-width' : 0.5,
         'stroke-dasharray' : '- '
      };
      var litConnAttribs = {
         stroke : '#4ECE4E',
         'stroke-width' : 2
      };

      // run thru each connection and draw a line from left to right
      var connections = $("#side-by-side").data("connections");
      var pathStr, line;
      $.each(connections, function(i, conn) {

         // grab co-ordinates of the connection and offset them by the
         // current scroll pos. This puts them relative to the visible
         // gutter area
         var leftPos = conn.left - leftTop;
         var rightPos = conn.right - rightTop;

         if (conn.type === "transposition") {
            pathStr = "M0," + leftPos + "L" + width + "," + rightPos;
            line = connectionPaper.path(pathStr);
            line.attr(moveLink);
            return true;
         }

         // if both are  out of the visible area, there is nothing
         // more that can be rendered - stop.
         if (leftPos > height && rightPos > height) {
            return false;
         }

         // if they both occur befor the visible area skip them
         if (leftPos < 0 && rightPos < 0) {
            return true;
         }

         // draw the line if its not too far away
         if (Math.abs(leftPos - rightPos) < height * 0.5) {
            pathStr = "M0," + leftPos + "L" + width + "," + rightPos;
            line = connectionPaper.path(pathStr);
            if (conn.lit) {
               line.attr(litConnAttribs);
            } else {
               if (Math.abs(leftPos - rightPos) < height * 0.2) {
                  line.attr(connAttribs);
               } else if (Math.abs(leftPos - rightPos) < height * 0.4) {
                  line.attr(faded1);
               } else {
                  line.attr(faded2);
               }
            }
         }
      });
   };

   var highlightDiff = function(diffEle) {
      diffEle.addClass("lit");
      var conn = diffEle.attr("juxta:connect-to");
      $("#diff-" + conn).addClass("lit");
      if ($("#diff-" + conn).width() === 0) {
         $("#diff-" + conn).html("&nbsp;");
      }

      var diffId = diffEle.attr("id");
      var connections = $("#side-by-side").data("connections");
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
         if ($(this).html() === "&nbsp;") {
            $(this).html("");
         }
      });
      $(".diff").removeClass("lit");
      var connections = $("#side-by-side").data("connections");
      $.each(connections, function(i, conn) {
         conn.lit = false;
      });
      renderConnections();
   };

   var renderTranspositionsGutter = function(hdrHeight, defaultHeight) {
      var leftGutterPaper = $("#left-gutter-div").data("paper");
      var rightGutterPaper = $("#right-gutter-div").data("paper");

      // start from the RIGHT; grab all of the text children
      // and find those that are of class 'MOVE'
      var outline = {
         stroke : '#000',
         'stroke-width' : 5
      };
      var fill = {
         stroke : '#CCC',
         'stroke-width' : 3
      };
      var connections = $("#side-by-side").data("connections");
      $("#right-witness-text").find(".move").each(function(index) {

         // calculate the extents of the braket for the left witness move
         var rightMoveTop = $(this).position().top - hdrHeight + 0.5 + $("#right-witness-text").scrollTop();
         var rightMoveHeight = $(this).height();
         if (rightMoveHeight === 0) {
            rightMoveHeight = defaultHeight;
         }
         var rightMoveBottom = rightMoveTop + rightMoveHeight + 0.5;
         var rightId = $(this).attr("id");

         // draw the line
         var pathStr = "M6," + rightMoveTop + "L6," + rightMoveBottom;
         var bracket = rightGutterPaper.path(pathStr);
         bracket.attr(outline);
         pathStr = "M5.5," + (rightMoveTop + 1) + "L5.5," + (rightMoveBottom - 1);
         bracket = rightGutterPaper.path(pathStr);
         bracket.attr(fill);

         // calculate the extents of the braket for the LEFT witness move
         var connectToId = "move-" + $(this).attr("juxta:connect-to");
         var leftMoveTop = $("#" + connectToId).position().top - hdrHeight + 0.5 + $("#left-witness-text").scrollTop();
         var leftMoveHeight = $("#" + connectToId).height();
         if (leftMoveHeight === 0) {
            leftMoveHeight = defaultHeight;
         }
         var leftMoveBottom = leftMoveTop + leftMoveHeight + 0.5;
         var leftId = $("#" + connectToId).attr("id");

         // see if this one is split into 2
         connectToId = connectToId + "-continued";
         var extend = $("#" + connectToId);
         if (extend.exists()) {
            leftMoveHeight += extend.height();
            leftMoveBottom += extend.height();
         }

         // draw the bracket
         pathStr = "M0," + leftMoveTop + "L0," + leftMoveBottom;
         bracket = leftGutterPaper.path(pathStr);
         bracket.attr(outline);
         pathStr = "M0.5," + (leftMoveTop + 1) + "05.5," + (leftMoveBottom - 1);
         bracket = leftGutterPaper.path(pathStr);
         bracket.attr(fill);

         // store the midpoint of the left/right bracket
         // heights as a connection point
         if (rightId.indexOf("-continued") > -1) {
            // This is a continuation of an existing move caused
            // when overlapping tag heirarchies are fixed. Dont add a
            // new connection; just extend the prior one
            var c = connections[connections.length - 1];
            c.right += rightMoveHeight / 2;
         } else {
            var connection = {};
            connection.left = leftMoveTop + leftMoveHeight / 2;
            connection.leftId = leftId;
            connection.right = rightMoveTop + rightMoveHeight / 2;
            connection.rightId = rightId;
            connection.lit = false;
            connection.type = "transposition";
            connections.push(connection);
         }
      });
   };

   /**
    * Render the left and right witness diff brackets in the narrow gutter
    * divs on either side of the connections area
    */
   var renderAlignmentGutter = function() {

      var leftGutterPaper = $("#left-gutter-div").data("paper");
      leftGutterPaper.clear();
      var rightGutterPaper = $("#right-gutter-div").data("paper");
      rightGutterPaper.clear();
      var connections = [];

      // grab some info needed to calculate positions of brackets
      var hdrHeight = $(".header").outerHeight();
      var defaultHeight = parseInt($('.witness-text').css('line-height'), 10) - 3;

      // first do transpositions so they are under the alignments
      renderTranspositionsGutter(hdrHeight, defaultHeight);

      // start from the RIGHT; grab all of the text children
      // and find those that are of class 'diff'
      var bracketAttribs = {
         stroke : '#181',
         'stroke-width' : 0.5,
         'stroke-linejoin' : 'round'
      };
      $("#right-witness-text").find(".diff").each(function(index) {

         // calculate the extents of the braket for the left witness diff
         var rightDiffTop = $(this).position().top - hdrHeight + $("#right-witness-text").scrollTop();
         var rightDiffHeight = $(this).height();
         if (rightDiffHeight === 0) {
            rightDiffHeight = defaultHeight;
         }
         var rightDiffBottom = rightDiffTop + rightDiffHeight;
         var rightId = $(this).attr("id");

         // draw the bracket
         var pathStr = "M6," + rightDiffTop + "L1," + rightDiffTop + "L1," + rightDiffBottom + "L6," + rightDiffBottom;
         var bracket = rightGutterPaper.path(pathStr);
         bracket.attr(bracketAttribs);

         // Get the LEFT witness diff
         var connectToId = "diff-" + $(this).attr("juxta:connect-to");
         if ($("#" + connectToId).exists()) {
            var leftDiffTop = $("#" + connectToId).position().top - hdrHeight + $("#left-witness-text").scrollTop();
            var leftDiffHeight = $("#" + connectToId).height();
            if (leftDiffHeight === 0) {
               leftDiffHeight = defaultHeight;
            }
            var leftDiffBottom = leftDiffTop + leftDiffHeight;
            var leftId = $("#" + connectToId).attr("id");

            // draw the bracket
            pathStr = "M0," + leftDiffTop + "L5," + leftDiffTop + "L5," + leftDiffBottom + "L0," + leftDiffBottom;
            bracket = leftGutterPaper.path(pathStr);
            bracket.attr(bracketAttribs);

            // store the midpoint of the left/right bracket
            // heights as a connection point
            var connection = {};
            connection.left = leftDiffTop + leftDiffHeight / 2;
            connection.leftId = leftId;
            connection.right = rightDiffTop + rightDiffHeight / 2;
            connection.rightId = rightId;
            connection.lit = false;
            connection.type = "diff";
            connections.push(connection);
         }
      });

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

      $("#side-by-side").data("connections", connections);
      renderConnections();
   };

   /**
    * Size the line bracket gutters and connection canvas to
    * match available area. Positions them appropriately
    */
   var initCanvas = function() {
      // figure out the gap between the text areas
      var top = $("#left-witness").position().top;
      var height = $("#left-witness").height();
      var left = $("#left-witness").position().left + $("#left-witness").outerWidth();
      var width = $("#right-witness").position().left - left;

      var imgLeft = left + (width - 32) / 2;
      $("#scroll-mode-img").css({
         "top" : (top + 1) + "px",
         "left" : (imgLeft) + "px"
      });

      // get height headers
      var hdrHeight = $(".header").outerHeight();
      top += hdrHeight;
      height -= hdrHeight;

      // position the left gutter
      $("#left-gutter-div").css({
         "top" : top + "px",
         "left" : left + "px"
      });
      $("#left-gutter-div").width(6);
      $("#left-gutter-div").height(height);

      // position the center connections paper
      left += 6;
      $("#connections-div").css({
         "top" : top + "px",
         "left" : left + "px"
      });
      $("#connections-div").width(width - 12);
      $("#connections-div").height(height);

      // position the right gutter
      left += $("#connections-div").outerWidth();
      $("#right-gutter-div").css({
         "top" : top + "px",
         "left" : left + "px"
      });
      $("#right-gutter-div").width(6);
      $("#right-gutter-div").height(height);

      // create papers to match size
      $("#left-gutter-div").data("paper", new Raphael(document.getElementById('left-gutter-div'), 6, getMaxWitnessHeight() + 100));
      $("#connections-div").data("paper", new Raphael(document.getElementById('connections-div'), width - 12, height));
      $("#right-gutter-div").data("paper", new Raphael(document.getElementById('right-gutter-div'), 6, getMaxWitnessHeight() + 100));
      renderAlignmentGutter();
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
         extraH = $(".header").outerHeight();
         newH = maxH - extraH;
      } else {
         var parent = $("#juxta-ws-content").parent();
         extraH = $(".header").outerHeight();
         parent.css("overflow-y", "hidden");
         newH = parent.height() - extraH;
      }

      if (newH < getMaxWitnessHeight()) {
         $(".witness-text").height(newH);
         $(".witness-text").css("overflow-y", "visible");
      } else {
         $(".witness-text").height(getMaxWitnessHeight());
         $(".witness-text").css("overflow-y", "hidden");
         $("#scroll-mode-img").css("visibility", "hidden");
      }

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
      $("#scroll-mode-img").data("locked", isLocked);

      if (isLocked === true) {
         $("#scroll-mode-img").attr("src", imgSrc + "/lock.gif");
         $("#left-witness-text").css("overflow-y", "hidden");
      } else {
         $("#scroll-mode-img").attr("src", imgSrc + "/unlock.gif");
         if (getMaxWitnessHeight() > $("#left-witness-text").height()) {
            $("#left-witness-text").css("overflow-y", "visible");
         }
      }

      initDocumentHeight();
      initCanvas();
      renderConnections();
   };

   var alignOnDiff = function(diffEle) {
      // find the connection containing the diff clicked
      // and determine witch witness contains is (left or right)
      var side = "left";
      var clickedId = diffEle.attr("id");
      var conn = null;
      var connections = $("#side-by-side").data("connections");
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
            leftTop = $("#left-gutter-div").scrollTop();
            $("#right-witness-text").scrollTop(leftTop + delta);
            $("#right-gutter-div").scrollTop($("#right-witness-text").scrollTop());
         } else {
            $("#left-witness-text").data("action", "match");
            delta = (conn.left - conn.right);
            rightTop = $("#right-gutter-div").scrollTop();
            $("#left-witness-text").scrollTop(rightTop + delta);
            $("#left-gutter-div").scrollTop($("#left-witness-text").scrollTop());
         }
         renderConnections();
      }
   };
   
    /*
    * find a connection given a position in either the right or left witness
    */
   var findConnection = function(tgtPos, side) {
      var connections = $("#side-by-side").data("connections");
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
      var leftTop = $("#left-gutter-div").scrollTop();

      // Special case: no diffs. Just sync 1 to 1
      var connections = $("#side-by-side").data("connections");
      if (connections.length === 0) {
         $("#right-gutter-div").scrollTop(leftTop);
         $("#right-witness-text").scrollTop(leftTop);
      } else {
         var maxLeftHeight = $("#left-witness-text")[0].scrollHeight;
         var leftDivHeight = $("#left-witness-text").outerHeight();

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
         $("#right-gutter-div").scrollTop(leftTop + delta);
         $("#right-witness-text").scrollTop(leftTop + delta);
      }

      // re-render al connection lines
      renderConnections();
   };

   var syncScrolling = function() {

      // Flag this as sync scroll event and grab the new top of the right text
      $("#left-witness-text").data("action", "sync");
      var rightTop = $("#right-gutter-div").scrollTop();
      var lastRight = $("#right-gutter-div").data("lastTop");
      var leftTop = $("#left-gutter-div").scrollTop();

      // Special case: no diffs. Just sync 1 to 1
      var connections = $("#side-by-side").data("connections");
      if (connections.length === 0) {
         $("#left-gutter-div").scrollTop(rightTop);
         $("#left-witness-text").scrollTop(rightTop);
      } else {
         var scrollDelta = rightTop - lastRight;
         var maxRightHeight = $("#right-witness-text")[0].scrollHeight;
         var rightDivHeight = $("#right-witness-text").outerHeight();

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

         // do the scroll on left to synch it with right
         $("#left-gutter-div").scrollTop(leftTop + posDelta);
         $("#left-witness-text").scrollTop(leftTop + posDelta);
      }

      // save this top as prior so deltas can be calculated
      $("#right-gutter-div").data("lastTop", rightTop);

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

      $(".witness-option").each(function(index) {
         if ($(this).attr("id") === witnessId) {
            $(this).attr("selected", "selected");
         }
      });

      var btn = $("#" + sourceButtonId);
      var top = btn.offset().top + btn.outerHeight() + 5;
      var left = btn.offset().left + btn.outerWidth() - $('.witnesses-popup').outerWidth();
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
         if ($(this).attr("selected") === "selected") {
            selectedId = $(this).attr("id");
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
         if ( window.Juxta.SideBySide.isLocked()) {
            $("#right-witness-text").data("action", "wheel");
            currTop = $("#right-witness-text").scrollTop();
            $("#right-witness-text").scrollTop(currTop -= (delta * 30));
            $("#right-gutter-div").scrollTop($("#right-witness-text").scrollTop());
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
         $("#right-gutter-div").scrollTop($("#right-witness-text").scrollTop());

         if ( window.Juxta.SideBySide.isLocked()) {
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

         // always keep the gutter in-sync with the text
         $("#left-gutter-div").scrollTop($("#left-witness-text").scrollTop());

         if ( window.Juxta.SideBySide.isLocked() === false) {
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

         // always keep the gutter in-sync with the text
         $("#right-gutter-div").scrollTop($("#right-witness-text").scrollTop());

         // right drives the sync sync. Handle this here.
         if ( window.Juxta.SideBySide.isLocked()) {
            var data = $("#right-witness-text").data("action");
            $("#right-witness-text").data("action", "none");
            if (data === "none") {
               syncScrolling();
            }
         } else {
            $("#right-gutter-div").scrollTop($("#right-witness-text").scrollTop());
            renderConnections();
         }
      });
   };
   
   window.Juxta.SideBySide.syncDocuments = function ( topOffset ) {
      var leftTop = $("#left-witness-text").scrollTop();
      var diffTop;
      var bestDiff = null;
      var smallestDist = 99999999;
      var testDist = 0;
      $.each( $("#left-witness-text").find(".diff"), function(i, diff) {
         diffTop = $(diff).offset().top - $("#left-witness-text").offset().top;
         if ( diffTop > 0 ) {
            testDist = diffTop - topOffset;
            if ( testDist > 0 && testDist < smallestDist ) {
               bestDiff =  diff;
               smallestDist = testDist;
            }
            
            var bottom = $("#left-witness-text").position().top + $("#left-witness-text").height();
            if ( diffTop > bottom) {
               return false;
            }
         }
      });
      
      setTimeout( function() {
         //$(bestDiff).css("color", "red");
         alignOnDiff( $(bestDiff) );
         }, 500);
   };

   /**
    * Main entry point for visualization: initialize everythign anf make it ready for use
    */
   window.Juxta.SideBySide.initialize = function() {
      $("#juxta-ws-content").css("overflow", "hide");
      $("#left-witness-text").data("action", "none");
      $("#right-witness-text").data("action", "none");

      var rightHeight = $("#right-witness").outerHeight();
      $("#right-witness").data("fullHeight", rightHeight);
      var leftHeight = $("#left-witness").outerHeight();
      $("#left-witness").data("fullHeight", leftHeight);
      $("#side-by-side").data("connections", []);
      $("#side-by-side").data("maxWitnessHeight", Math.max(rightHeight, leftHeight));
      $("#side-by-side").data("isResizing", false);
      $("#right-gutter-div").data("lastTop", 0);

      // initially, scrolling is LOCKED. Must be set before
      // the calls to init height/canvas
      $("#scroll-mode-img").data("locked", true);

      // init height, scrollbars and raphael canvas objects
      initDocumentHeight();
      initCanvas();

      // Setup click handling that allows diffs to auto-align when clicked
      $(".witness-text").on("click", ".diff", function(event) {
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

      initMouseWheelScrollHandling();
      initWitnessScrollHandling();
      
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
   };
   
   // Let the world know that the side-by-side code is now loaded and can be initialized
   $("body").trigger('sidebyside-loaded');

   $(window).resize(function() {
      if ($("#right-witness-text").exists()) {
         if ($("#side-by-side").data("isResizing") === false) {
            $("#side-by-side").data("isResizing", true);
            setTimeout(function() {
               initDocumentHeight();
               initCanvas();
               renderConnections();
               $("#side-by-side").data("isResizing", false);
            }, 100);
         }
      }
   });

});

