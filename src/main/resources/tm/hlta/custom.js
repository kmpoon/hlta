// this file assumes a variable nodes is used to hold the nodes of the jstree
// and a <div> with id 'jstree' as a placeholder of the tree.

function getInputValue(id, defaultValue) {
	var value = $(id).val();
	if (value.length <= 0)
		value = defaultValue;

	return value;
}

function findLevel(node) {
	var l = node.data.level - 1;
	if (typeof levels[l] == 'undefined')
		levels[l] = [];

	levels[l].push(node)

	$.each(node.children, function(i, v) {
		findLevel(v);
	})
}

// find the node levels in the tree
var levels = [];
$.each(nodes, function(i, v) {
	findLevel(v)
});

//find the min and max year in the documents
var minYear = 1000000;
var maxYear = 0;
$.each(documents, function(i, d) {
 if (d.year > maxYear)
     maxYear = d.year;
 if (d.year < minYear)
     minYear = d.year;
})

function generateTopicDocumentTable(topic) {
    var topicDocuments = topicMap[topic];

    var rows = [];
    for (var i = 0; i < topicDocuments.length; i++) {
        if (i >= 500) {
            rows.push("<tr><td>etc.</td></tr>");
            break;
        }
        var d = topicDocuments[i];
        var doc = documents[d[0]];
        rows.push("<tr><td>" + doc.source + "</td><td>" + doc.year + "</td><td>" + doc.title + "</td><td>" + d[1] + "</td></tr>");
    }

    var table = $("<table class=\"tablesorter\"><thead><tr><th>Conf</th><th>Year</th><th>Title</th><th>Prob</th></tr></thead></table>").append("<tbody/>").append(rows.join(""));
    table.tablesorter();
    return table;
}

function generateCountTable(topic) {
    var topicDocuments = topicMap[topic];
    var counts = {};
    for (var year = minYear; year <= maxYear; year++) {
        counts[year] = 0;
    }

    $.each(topicDocuments, function(i, d) {
        var doc = documents[d[0]];
        counts[doc.year] = counts[doc.year] + 1;
    })

    var headRow = $("<tr/>");
    var bodyRow = $("<tr/>");
    for (var year = minYear; year <= maxYear; year++) {
        headRow.append("<td>" + year + "</td>");
        bodyRow.append("<td>" + counts[year] + "</td>");
    }
    var table = $("<table class=\"tablesorter\"/>").append("<thead/>").append("<tbody/>");
    table.children("thead").append(headRow);
    table.children("tbody").append(bodyRow);

    return table;
}

function constructTree(n) {
	$("#jstree").on("changed.jstree", function(e, data) {
		// show a pop-up when a node has been selected
        if (data.action == "select_node") {
            var content = $("<div class='white-popup'/>");
            content.append($("<h3 class='popup-heading'>" + data.node.text + " (" + data.node.id + ")</h3>"));

            var topicDocuments = topicMap[data.node.id]

            content.append(generateCountTable(data.node.id));
            content.append(generateTopicDocumentTable(data.node.id));

            $.magnificPopup.open({
                items: {
                    src: content,
                    type: 'inline'
                }
            });
        }
    }).jstree({
		"core" : {
			"data" : n,
			"themes" : {
				"icons" : false
			}
		},
		"search": {
            "case_insensitive": true,
            "show_only_matches" : true,
            "show_only_matches_children": true
        },
        "plugins": ["search"]
	});
}

function showLevels(top, bottom) {
	var current = $('#jstree').jstree(true);
	if (typeof current != 'undefined')
		current.destroy();

	for (var i = top; i > bottom; i--) {
		$.each(levels[i - 1], function(i, v) {
			v.state.opened = true;
		})
	}

	for (var i = bottom; i > 0; i--) {
		$.each(levels[i - 1], function(i, v) {
			v.state.opened = false;
		})
	}

	constructTree(levels[top-1]);
}

$(function() {
	constructTree(nodes);

    // set the default values of the levels
    $("#top-input").val(levels.length)
    $("#bottom-input").val(1)
  
    $('#level-button').click( function() {
      var top = getInputValue('#top-input', 1000000)
      var bottom = getInputValue('#bottom-input', 1)
      showLevels(top, bottom);
    })
    
    $("#filter-button").click( function() {
        var searchString = $("#search-input").val();
        $('#jstree').jstree('search', searchString);
    });

    $("#clear-button").click( function() {
        $('#jstree').jstree(true).clear_search();
    	$("#search-input").val("");
    });
});
