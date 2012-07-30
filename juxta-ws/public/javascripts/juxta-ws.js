

$(document).ready(function() {
    if ( isEmbedded() == false ) {
        initScrollerHeight();
        initializeMenu();

        $("#change-workspace").click(function() {
            showChangeWorkspacePop();
        });
        $("#ok-workspace").click(function() {
            changeWorkspace();
        });
        $("#cancel-workspace").click(function() {
            $('#select-ws').css("visibility", "hidden");
        });
    
        $("#new-workspace").click(function() {
            showCreateWorkspacePop();
        });
        $("#cancel-create").click(function() {
            $('#create-ws').css("visibility", "hidden");
        });
        $("#ok-create").click(function() {
            createWorkspace();
        });
    }
    
    $("body").on("heatmap-loaded", function() {
       Juxta.Heatmap.initialize();
    });
    $("body").on("sidebyside-loaded", function() {
       Juxta.SideBySide.initialize();
    });
});

function initializeMenu() {
    var page = $('#page');
    var tab = $("#" + page.text());
    tab.addClass("activeMenu");
}

$(window).resize(function() {
    if ( isEmbedded() == false ) {
        initScrollerHeight();
    }
});

function createWorkspace() {
    var name = $("#ws-name").val();
    var desc = $("#ws-desc").val();
    if ( name == "" ) {
        alert("Please enter a new workspace name!");
    } else {
        var wsUrl = $('#serviceUrl').text() + "/workspace";
        var json = "{\"name\": \""+name+"\", description\": \""+desc+"\"}";
        var currPage = $('#page').text();
        if ( currPage == "parse_profile" ) {
            baseUrl += "/profile/parse";
        } 
        var newUrl = $('#serviceUrl').text() + "/" + name +"/" + currPage;
        
        $.ajax({
            url: wsUrl,
            type: 'POST',
            async:false,
            contentType: 'application/json',
            data: json,
            success: function() { 
                window.location = newUrl; 
            },
            error: function(jqXHR, textStatus, errorThrown) {
                alert("Unable to create new workspace!\n\n"+jqXHR.responseText);
            }
        });
    }
}

function showCreateWorkspacePop() {
    $('#select-ws').css("visibility", "hidden");
    $('#create-ws').css("visibility", "visible");
    var top = $('#select-ws').position().top;
    var left = $('#select-ws').position().left;
    $('#create-ws').width(240);
    $('#create-ws').css({
        "top" : (top) + "px",
        "left" : (left-40) + "px"
    });
}

function changeWorkspace() {
    $('#select-ws').css("visibility", "hidden");
    var currWorkspace = $("#curr-workspace").text();
    
    // find the selected ID from the list
    var newWorkspace;
    $(".workspace-option").each(function(index) {
        if ( $(this).attr("selected") == "selected") {
            newWorkspace = $(this).text();
        }
    });
    
    if ( newWorkspace !=  currWorkspace ) {
        var page = $('#page').text();
        var baseUrl = $('#serviceUrl').text() + "/" + newWorkspace;
        if ( page == "parse_profile" ) {
            baseUrl += "/profile/parse";
        } else {
            baseUrl += "/" + page;
        }
        window.location = baseUrl;
    }
}

function showChangeWorkspacePop() {
    var currId = $("#curr-workspace-id").text();
    $(".workspace-option").each(function(index) {
        if ( $(this).attr("id") == currId) {
            $(this).attr("selected", "selected");
        }
    });
    
    var btn = $("#change-workspace");
    var top = btn.offset().top + btn.outerHeight() + 5;
    var left = btn.offset().left + btn.outerWidth() - $('#select-ws').outerWidth();
    $('#select-ws').css({
        "top" : top + "px",
        "left" : left + "px"
    });
    $('#select-ws').css("visibility", "visible");
}

function initScrollerHeight() {
    var windowH = $(window).height();
    var titleBar = $(".title");
    var newH = windowH;
    var navBottom = $(".title").offset().top+$(".title").outerHeight();
    
    newH = windowH - navBottom - $(".footer").outerHeight()-18;
    $(".content-scroller").height( newH );
    
    var showScroll = false;
    if ( $("#juxta-ws-content").height() < newH ) {
        $(".content-scroller").css("overflow-y", "hidden");
    }

    var cs = document.getElementById("content-scroller");
    if ( cs.scrollWidth <=  cs.clientWidth ) {
        $(".content-scroller").css("overflow-x", "hidden");
    }
}