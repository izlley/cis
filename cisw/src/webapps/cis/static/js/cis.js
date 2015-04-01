(function($)
{
    $(document).ready(init);

  // Key => Value( query, setInterval obj )
  var gQueryMap = new Object();
  var gCiswUrl = 'http://192.168.16.75:2848';

    function init()
    {
        regEventHandler();
        initDtree();
        initDragTable();
        initExtra();
        initAllChart();
    };

    function regEventHandler()
    {
        $("#BasicSearch").click(function(){
            $(".BasicSearch label.over").css({"text-indent":"-10000px"});
        });
        $("#BasicSearch").blur(function(){
            if( $(this).val() === '' )
            {
                $(".BasicSearch label.over").css({"text-indent":"2px"});
            }
        });
    };

  function initExtra()
  {
    addboxFitting();

    $("#zoomModal .close").click(function(){
      $('#zoomModal').modal('hide');
    });

    $("#addModal .exit").click(function(){
      $('#addModal').modal('hide');
      $('#newchart').trigger('blur');
      $('#addbox>p>a').trigger('blur');
      $('#metricshelper').popover('hide');
    });

    $("#addModal").bind('hide', function(){
      clearAddchartModal();
      $('#newchart').trigger('blur');
      $('#addbox>p>a').trigger('blur');
    });

    $("#addModal").bind('blur', function(){
      $('#newchart').trigger('blur');
      $('#addbox>p>a').trigger('blur');
    });

    $("#login").bind('click', function(){
      if( $('#loginModal').length === 0 )
      {
        $('body').children().last().after(gLoginModalHtml);
      }
      
      $("#loginModal .close").click(function(){
        $('#loginModal').modal('hide');
        $('#login').trigger('blur');
      });

      $("#loginModal").bind('blur', function(){
        $('#login').trigger('blur');
      });
    });

    $("#addcol").click(function(){
      var sClass;
      $("ul#draglist li").each(function(){
        sClass = $(this).attr('class');
        if(sClass == "span12")
        {
          $(this).removeClass("span12").addClass("span6");
        }
        else if(sClass == "span6")
        {
          $(this).removeClass("span6").addClass("span4");
        }
        else if(sClass == "span4")
        {
          $(this).removeClass("span4").addClass("span3");
        }
        else if(sClass == "span3")
        {

        }
      });

      sClass = $("div#addbox").attr('class');
      if(sClass == "span12")
       {
         $("div#addbox").removeClass("span12").addClass("span6");
       }
       else if(sClass == "span6")
       {
         $("div#addbox").removeClass("span6").addClass("span4");
       }
       else if(sClass == "span4")
       {
         $("div#addbox").removeClass("span4").addClass("span3");
       }
       else if(sClass == "span3")
       {
         alert('Maximum number of columns exceeded.(4)');
       }
       addboxFitting();
    });

    $("#deletecol").click(function(){
      var sClass;
      $("ul#draglist li").each(function(){
        sClass = $(this).attr('class');
        if(sClass == "span3")
        {
          $(this).removeClass("span3").addClass("span4");
        }
        else if(sClass == "span4")
        {
          $(this).removeClass("span4").addClass("span6");
        }
        else if(sClass == "span6")
        {
          $(this).removeClass("span6").addClass("span12");
        }
        else if(sClass == "span12")
        {

        }
      });

      sClass = $("div#addbox").attr('class');
      if(sClass == "span3")
      {
        $("div#addbox").removeClass("span3").addClass("span4");
      }
      else if(sClass == "span4")
      {
        $("div#addbox").removeClass("span4").addClass("span6");
      }
      else if(sClass == "span6")
      {
        $("div#addbox").removeClass("span6").addClass("span12");
      }
      else if(sClass == "span12")
      {
        alert('It must contain at least one column.');
      }
      addboxFitting();
    });

    $("#addmetrictab").click(addMetricTab);
    $("#delmetrictab").click(delMetricTab);

    $( "#inputSDate" ).datepicker({
      showButtonPanel: true,
      dateFormat: "yy/mm/dd",
      autoSize: true,
      showSliderPanel: true
    });
    $( "#inputEDate" ).datepicker({
      showButtonPanel: true,
      dateFormat: "yy/mm/dd",
      autoSize: true,
      showSliderPanel: true
    });

    $("#inputSDate").focus(function(){
      addDatePicSlider();
    });
    $("#inputEDate").focus(function(){
      addDatePicSlider();
    });

    //$("#inputDescription").focus(function(){
    //  $("#inputDescription").val("");
    //});

    initDateBtnHandler();

    $("#querygen").click(function(){
      queryGen('');
    });

    $("#draglist .chartheader div.dropdown").bind('focusout', function(){
      $(this).prev().trigger('blur');
    });

    $('body').children().last().after(gAddPageHtml);
    $('#addpage').bind('click', function(){
      $('#addPageModal .modal-header h4').replaceWith('<h4>Add page</h4>');
      $('#addPageModal').modal('toggle');

      $('#AddPageOk').unbind();
      $('#AddPageOk').bind('click', function(){
        if( $('#inputPage').val() != '' )
        {
          $('#dtree').dynatree('getRoot').addChild(
            { title : $('#inputPage').val() },
            $('#dtree').dynatree('getRoot').getChildren()[0]
          );
          $('#addPageModal').modal('hide');
          $('#inputPage').val('');
        }
        else
        {
          $('#inputPage').trigger('focus');
        }
      });
    });

    $('#adddir').bind('click', function(){
      $('#addPageModal .modal-header h4').replaceWith('<h4>Add directory</h4>');
      $('#addPageModal').modal('toggle');

      $('#AddPageOk').unbind();
      $('#AddPageOk').bind('click', function(){
        if( $('#inputPage').val() != '' )
        {
          $('#dtree').dynatree('getRoot').addChild(
            { title : $('#inputPage').val(), isFolder: true },
            $('#dtree').dynatree('getRoot').getChildren()[0]
          );
          $('#addPageModal').modal('hide');
          $('#inputPage').val('');
        }
        else
        {
          $('#inputPage').trigger('focus');
        }
      });
    });

    $('#doplot').bind('click', function(){
      var sPlotname = "plot" + ($('#draglist').children().length + 1);

      // validate inputs
      if($('#inputTitle').val().length === 0)
      {
        $('#inputTitle').trigger('focus');
        return;
      }
      if($('#inputFrequency').val().length === 0)
      {
        $('#inputFrequency').trigger('focus');
        return;
      }
      if( $('#inputQuery').val() == '' )
      {
        if( queryGen(sPlotname) === false )
        {
          return;
        }
      }
      else
      {
        gQueryMap[sPlotname] = [$('#inputQuery').val(),null,null];
      }

      //$('#draglist').children().last().after(gChartWizHtml);
      $('#draglist').append(gChartWizHtml);

      $('#draglist').children().last().attr("id",sPlotname).addClass($("div#addbox").attr('class'));

      $('#' + sPlotname + ' .closechartbox').click(function(){
        var sChartname = $(this).parents().filter("li").attr("id");
        if( gQueryMap[sChartname][0] !== null )
        {
          gQueryMap[sChartname][0] = '';
        }
        if( gQueryMap[sChartname][1] !== null )
        {
          clearInterval(gQueryMap[sChartname][1]);
          gQueryMap[sChartname][1] = null;
        }
        if( gQueryMap[sChartname][2] !== null )
        {
          clearInterval(gQueryMap[sChartname][2]);
          gQueryMap[sChartname][2] = null;
        }
        $(this).unbind();
        $(this).parents().filter("li").first().remove();
      });

      var sText = '';
      var sNode = $("#dtree").dynatree("getActiveNode");
      if( sNode !== null )
      {
        for( sText = sNode.data.title ; sNode.getLevel() > 1 ; )
        {
          sNode = sNode.getParent();
          sText = sNode.data.title + ' &raquo; ' + sText;
        }

        $('#' + sPlotname + ' .content').html(sText);
      }

      $('#' + sPlotname + ' .title').text($('#addModal #inputTitle').val());

      if( (sText = $('#addModal #inputTags').val()) !== '' )
      {
        var sArr = sText.split(',');
        for(var i = 0 ; sArr.length > i ; i++)
        {
          $('#' + sPlotname + ' #taginfo').append(
            '<a href="#"><span class="label label-inverse">'+ sArr[i] + '</span></a>');
        }
      }

      if( (sText = $('#addModal #inputDescription').val()) !== '' )
      {
        $('#' + sPlotname + ' .charttext p').text(sText);
      }

      var sFreq = $('#inputFrequency').val();
      switch( $('#selFrequency').val() )
      {
        case 'seconds':
          sFreq *= 1000;
          break;
        case 'minutes':
          sFreq *= 60000;
          break;
        case 'hours':
          sFreq *= 3600000;
          break;
        default:
          sFreq *= 1000;
          break;
      }

      // set time ticker
      if( gQueryMap[sPlotname][2] !== null )
      {
        clearInterval(gQueryMap[sPlotname][2]);
        gQueryMap[sPlotname][2] = null;
      }
      gQueryMap[sPlotname][2] = setInterval( 
        function(){
            $('#' + sPlotname + ' #updatetext .ticker').text( parseInt($('#' + sPlotname + ' #updatetext .ticker').text())+1 );
        }, 1000 );

      $('#' + sPlotname + ' #updatetext .freqt').text( sFreq/1000 );

      // clear
      $('#addModal').modal('hide');
      $('#newchart').trigger('blur');
      $('#addbox>p>a').trigger('blur');
      $('#metricshelper').popover('hide');

      // do plot!
      doPlot(sPlotname, sFreq);
    });

    $('#metricshelper').click(function(e) {
        e.preventDefault();

        // AJAX call
        $(this).popover({
          html: true,
          placement: 'up',
          trigger: 'manual',
          content: '<table cellpadding="0" cellspacing="0" border="0" class="display" id="metricsTable"></table>'
        }).popover('show');

        $('#metricsTable').dataTable( {
          "bProcessing": true,
          "sScrollY": "380",
          "bScrollCollapse": false,
          "iDisplayLength": 10,
          "sAjaxSource": "s/cis/mypage/arrays.json",
          "oLanguage": {
            "sLengthMenu": ""
          },
          "aoColumns": [
            { "sTitle": "Engine" },
            { "sTitle": "Browser" },
            { "sTitle": "Platform" },
            { "sTitle": "Version", "sClass": "center" },
            { "sTitle": "Grade", "sClass": "center" }
        ] } );

        /*
        $('#metricsTable').dataTable( {
        "aaData": [
            [ "Trident", "Internet Explorer 4.0", "Win 95+", 4, "X" ],
            [ "Trident", "Internet Explorer 5.0", "Win 95+", 5, "C" ],
            [ "Trident", "Internet Explorer 5.5", "Win 95+", 5.5, "A" ],
            [ "Trident", "Internet Explorer 6.0", "Win 98+", 6, "A" ],
            [ "Trident", "Internet Explorer 7.0", "Win XP SP2+", 7, "A" ],
            [ "Gecko", "Firefox 1.5", "Win 98+ / OSX.2+", 1.8, "A" ],
            [ "Gecko", "Firefox 2", "Win 98+ / OSX.2+", 1.8, "A" ],
            [ "Gecko", "Firefox 3", "Win 2k+ / OSX.3+", 1.9, "A" ],
            [ "Webkit", "Safari 1.2", "OSX.3", 125.5, "A" ],
            [ "Webkit", "Safari 1.3", "OSX.3", 312.8, "A" ],
            [ "Webkit", "Safari 2.0", "OSX.4+", 419.3, "A" ],
            [ "Webkit", "Safari 3.0", "OSX.4+", 522.1, "A" ]
        ],
        "aoColumns": [
            { "sTitle": "Engine" },
            { "sTitle": "Browser" },
            { "sTitle": "Platform" },
            { "sTitle": "Version", "sClass": "center" },
            { "sTitle": "Grade", "sClass": "center" }
        ]
    } );
*/

/*
        $('#metricsTable').dataTable( {
          "bProcessing": true,
          "aaData": '{ [ "Trident","Internet Explorer 4.0","Win 95+", "4", "X" ],'+
          '[   "Trident",  "Internet Explorer 5.0",   "Win 95+",  "5",  "C"  ] }' 
        });
*/
      $('#metricsClose').unbind().bind('click', function(){
        $('#metricshelper').popover('hide');
      });

        // show metrics table
        //$(this).text('<b>asdfasdff</b>');

    });

    // The first auto-completion of tagkeys
    autoCompleteTK('metric1');
    autoCompleteTV($('#metric1 #metrictags .tagval'));
    autoCompleteMetrics('metric1');
  };

  function autoCompleteTK( aTabId )
  {
    $('#'+aTabId+' #inputMetric').bind('blur', function(){
      autoCompleteTKCB( $(this), aTabId );
    });
    $('#'+aTabId+' #inputMetric').bind('keyup', function(e){
        var code = e.keyCode || e.which; 
        if(code == 13)
        { //Enter keycode
          autoCompleteTKCB( $(this), aTabId );
        }
    });
  }

  function autoCompleteTKCB( aNode, aTabId )
  {
      if( aNode.val() != '' )
      {
        // Call AJAX
        $.getJSON( 'suggest?type=tagk&q=*'+aNode.val(), function(json){
          if( json != null )
          {
            //var tagArr = eval(json.d);
            //clear tag input
            $('#'+aTabId+' #metrictags>label').each(function(i){
                if( i > 0 )
                {
                    $(this).remove();
                }
            });
            $('#'+aTabId+' #metrictags>div').each(function(i){
                if( i > 0 )
                {
                    $(this).find('.tagval').unbind();
                    $(this).remove();
                }
            });

            for( var i=0, len=$('#'+aTabId+' #metrictags').children('div').length ; json.length > i ; i++ )
            {
              if( i === 0 )
              {
                $('#'+aTabId+' #metrictags .controls').first().
                  children('.tagkey').val(json[i]);
              }
              else
              {
                if( len-1 < i )
                {
                  $('#'+aTabId+' #metrictags').children().last().after('<label class="control-label">tag'+(i+1)+'</label>'+
                    '<div class="controls"><input class="tagkey input-small" type="text" placeholder="tagKey">'+
                    ' = '+
                    '<input class="tagval input-small" type="text" placeholder="tagValue"></div>');

                  autoCompleteTV( $('#'+aTabId+' #metrictags .controls').eq(i).children('.tagval') );
                }
                $('#'+aTabId+' #metrictags .controls').eq(i).children('.tagkey').val(json[i]);
              }
            }
          }
        });
      }
  }

  function autoCompleteTV( aNode )
  {
    aNode.bind('keyup', function(e){
      autoCompleteTVCB($(this));
    });
  }

  function autoCompleteTVCB( aNode )
  {
    if( aNode.val() != '' )
    {
      // Call AJAX
      $.getJSON( 'suggest?type=tagv&q='+aNode.val(), function(json){
        if( json != null )
        {
          aNode.autocomplete({
            source: json
          });
        }
      });
    }
  }

  function autoCompleteMetrics( aTabId )
  {
    $('#'+aTabId+' #inputMetric').bind('keyup', function(e){
      autoCompleteMetricsCB($(this));
    });
  }

  function autoCompleteMetricsCB( aNode )
  {
    if( aNode.val() != '' )
    {
      // Call AJAX
      $.getJSON( 'suggest?type=metrics&q='+aNode.val(), function(json){
        if( json != null )
        {
          aNode.autocomplete({
            source: json
          });
        }
      });
    }
  }

  var gOptions = {
      legend: { show: true,
                    noColumns: 2,
                    position: "nw",
                    sorted: true },
      lines: { show: true },
      points: { show: false },
      grid: { hoverable: true },
      xaxis: {
        mode: "time",
        timeformat: "%m/%d-%h:%M"
      },
      selection: { mode: "xy" }
  };
  var gZoomOptions = {
      legend: { show: true,
                    noColumns: 2,
                    position: "nw",
                    sorted: true },
      lines: { show: true },
      points: { show: true },
      grid: { hoverable: true },
      xaxis: { 
        mode: "time",
        timeformat: "%m/%d-%h:%M"
      },
      selection: { mode: "xy" }
  };

  function doPlot( aPlotname, aFreq )
  {
    // AJAX call

    // parse JSON data
    // Plot it!!

    var data = [];
    var alreadyFetched = {};

    var originsel = $("#"+aPlotname+" #chart");

    var zooming = null;
    var origin = null;

    var iteration = 0;

    function fetchData() 
    {
      iteration++;
      
      // Call AJAX
      $.getJSON( "q?" + gQueryMap[aPlotname][0], function(json){
        if( json.err != null )
        {
            $(originsel).addClass('alert').addClass('alert-error').
                html('<span class="icon16x16-yellowHealth"></span><strong> ERR : </strong>' + json.err);
        }
        else if( json.data.length == 0 )
        {
            $(originsel).addClass('alert').
                html('<span class="icon16x16-yellowHealth"></span><strong> Warnning : </strong>' + 'No data was retrieved. Please expand the time span.');
        }
        else
        {
            data = json.data;
            if( origin !== null )
            {
                origin.shutdown();
                origin = null;
            }
            $(originsel).removeClass('alert').removeClass('alert-error');
            origin = $.plot(originsel, data, gOptions);
            $('#' + aPlotname + ' #updatetext .ticker').text(0);
        }
      });
    }

    fetchData();
    if( gQueryMap[aPlotname][1] !== null )
    {
      clearInterval(gQueryMap[sPlotname][1]);
      gQueryMap[aPlotname][1] = null;
    }
    gQueryMap[aPlotname][1] = setInterval( function(){
        fetchData();
      }, aFreq
    );

    $("#zoomingchart").bind("plotselected", function (event, ranges) {

      // clamp the zooming to prevent eternal zoom
      if (ranges.xaxis.to - ranges.xaxis.from < 0.00001)
          ranges.xaxis.to = ranges.xaxis.from + 0.00001;
      if (ranges.yaxis.to - ranges.yaxis.from < 0.00001)
          ranges.yaxis.to = ranges.yaxis.from + 0.00001;
        
      // do the zooming
      if( zooming !== null )
      {
          zooming.shutdown();
          zooming = null;
      }
      zooming = $.plot($("#zoomingchart"), data, 
                      $.extend(true, {}, gZoomOptions, {
                          xaxis: { min: ranges.xaxis.from, max: ranges.xaxis.to },
                          yaxis: { min: ranges.yaxis.from, max: ranges.yaxis.to }
                      }));
        
      // don't fire event on the overview to prevent eternal loop
      //zooming.setSelection(ranges, true);
    });

    $(originsel).bind("plotselected", function (event, ranges) {
      $('#zoomModal').modal('show');

      // clamp the zooming to prevent eternal zoom
      if (ranges.xaxis.to - ranges.xaxis.from < 0.00001)
          ranges.xaxis.to = ranges.xaxis.from + 0.00001;
      if (ranges.yaxis.to - ranges.yaxis.from < 0.00001)
          ranges.yaxis.to = ranges.yaxis.from + 0.00001;

      // do the zooming
      if( zooming !== null )
      {
          zooming.shutdown();
      }
      zooming = $.plot($("#zoomingchart"), data, 
                      $.extend(true, {}, gZoomOptions, {
                          xaxis: { min: ranges.xaxis.from, max: ranges.xaxis.to },
                          yaxis: { min: ranges.yaxis.from, max: ranges.yaxis.to }
                      }));
      origin.clearSelection(true);
    });

    var previousPoint = null;
    $(originsel).bind('plothover',function(event, pos, item) {
      if(item)
      {
        if(previousPoint != item.dataIndex)
        {
          previousPoint = item.dataIndex;
                    
          $("#tooltip").remove();
          var x = item.datapoint[0].toFixed(2),
              y = item.datapoint[1].toFixed(2);
                    
          showTooltip(item.pageX, item.pageY, y);
        }
      }
      else 
      {
        $("#tooltip").remove();
        previousPoint = null;            
      }
    });

    var previousPoint2 = null;
    $("#zoomingchart").bind('plothover',function(event, pos, item) {
      if(item)
      {
        if(previousPoint2 != item.dataIndex)
        {
          previousPoint2 = item.dataIndex;
                    
          $("#tooltip").remove();
          var x = item.datapoint[0].toFixed(2),
              y = item.datapoint[1].toFixed(2);
                    
          showTooltip2(item.pageX, item.pageY, y);
        }
      }
      else 
      {
        $("#tooltip").remove();
        previousPoint2 = null;            
      }
    });
  };

  function showTooltip(x, y, contents) {
      $('<div id="tooltip">' + contents + '</div>').css( {
          position: 'absolute',
          display: 'none',
          top: y - 25,
          left: x + 5,
          border: '1px solid #fdd',
          padding: '2px',
          'background-color': '#fee',
          opacity: 0.80
      }).appendTo("body").fadeIn(200);
  }

  function showTooltip2(x, y, contents) {
      $('<div id="tooltip">' + contents + '</div>').css( {
          position: 'fixed',
          display: 'none',
          top: y - 25,
          left: x + 5,
          border: '1px solid #fdd',
          padding: '2px',
          'background-color': '#fee',
          opacity: 0.80
      }).appendTo("#zoomModal").fadeIn(200);
  }

  function addDatePicSlider()
  {
    $( "#datepicSlider" ).slider({
      value:0,
      min: 0,
      max: 1440,
      step: 10,
      slide: function( event, ui ) {
        $( "#datepicTime" ).val( 
          ((Math.floor(ui.value/60)<10)?("0"+Math.floor(ui.value/60)) : Math.floor(ui.value/60)) + 
          ":" + 
          (((ui.value%60)==0)? "00" : ui.value%60) );
      }
    });
    $( "#datepicTime" ).val( "00:00" );
  }

  function addMetricTab()
  {
    var sLen = $("#metricContent").children().length;

    $("#metrictab>ul").children().each(function(){
      $(this).removeClass("active");
    });
    $("#metrictab>ul").children().last().prev().prev().after(
      "<li class=\"active\"><a href=\"#metric" + (sLen + 1) +
      "\" data-toggle=\"tab\">" + (sLen + 1) + "</a></li>");
    //$("#metrictab>ul").children().last().click();

    $("#metricContent").children().last().after(gMetricTabModalHtml).
      next().attr("id","metric"+(sLen+1));

    autoCompleteTK("metric"+(sLen+1));
    autoCompleteTV( $('#metricContent').children().last().find('#metrictags .tagval') );
    autoCompleteMetrics("metric"+(sLen+1));

    $('#metricContent').children().each( function() {
      $(this).removeClass('active').removeClass('in');
    });
    
    $("#metric"+(sLen+1)).addClass('active').addClass('in');
  };

  function delMetricTab()
  {
    var $metricCxt = $("#metricContent").children();
    var $metricTab = $("#metrictab>ul").children();

    if( $metricTab.length > 3 )
    {
      $("#metrictab>ul").children().last().prev().prev().remove();
    }
    if( $metricCxt.length > 1 )
    {
      $("#metricContent").children().last().find('#inputMetric').unbind();
      $("#metricContent").children().last().find('#metrictags .tagval').each(function(i){
        $(this).unbind();
      });
      $("#metricContent").children().last().remove();
    }

    if( $("#metrictab>ul").children().find('.active').length == 0 )
    {
      $("#metrictab>ul").children().last().prev().prev().addClass('active');
    }

    if( $("#metricContent").children().find('.active').length == 0 )
    {
      $("#metricContent").children().last().addClass('active').addClass('in');
    }
  };

  function addboxFitting()
  {
    var sWidth = $("#addbox").width();
    var sHeight = $("#addbox").height();
    $("#addbox p").css({"left":sWidth/2-113/2,"top":sHeight/2-28/2});
  };

    function initDragTable()
    {
        $("#draglist").dragsort({ dragSelector: "li>div>.chartheader", dragBetween: false,
            placeHolderTemplate: "<li class='span4 placeHolder'><div></div></li>" });
    };

  function initDateBtnHandler()
  {
    $("#last15min").bind('click', function(){
      $("#inputSDate").val('15m-ago');
      $("#inputEDate").val('');
    });
    $("#last30min").bind('click', function(){
      $("#inputSDate").val('30m-ago');
      $("#inputEDate").val('');
    });
    $("#last1hour").bind('click', function(){
      $("#inputSDate").val('1h-ago');
      $("#inputEDate").val('');
    });
    $("#last6hour").bind('click', function(){
      $("#inputSDate").val('6h-ago');
      $("#inputEDate").val('');
    });
    $("#last12hour").bind('click', function(){
      $("#inputSDate").val('12h-ago');
      $("#inputEDate").val('');
    });
    $("#last1day").bind('click', function(){
      $("#inputSDate").val('1d-ago');
      $("#inputEDate").val('');
    });
  };

  function clearAddchartModal()
  {
    $("#addModal .modal-body").scrollTop(0);
    $("#inputTitle").val('');
    $("#inputFrequency").val('');
    $("#selFrequency").val('seconds');
    $("#inputTags").val('');
    $("#inputDescription").val('');
    $("#inputSDate").val('');
    $("#inputEDate").val('');
    $("#selCarttype").val('Line');
    
    $sMetrictab = $("#metrictab>ul").children();
    for(var i = $sMetrictab.length-2 ; i > 1 ; i--)
    {
      $sMetrictab.eq(i-1).remove();
      if( i === 2)
      {
        $sMetrictab.eq(0).addClass('active');
      }
    }

    $("#metricContent").children().each(function(i){
      if(i !== 0)
      {
        $(this).remove();
      }
      else
      {
        $(this).addClass('active').addClass('in');
      }
    });

    $('#metrictags').children('label').each(function(i){
      if(i !== 0)
      {
        $(this).remove();
      }
    });
    $('#metrictags').children('div').each(function(i){
      if(i !== 0)
      {
        $(this).find('.tagval').unbind();
        $(this).remove();
      }
    });

    $("#metric1 input").val('');
    $("#metric1 #selAggregator").val('Sum');
    $("#metric1 #inputRate").attr('checked', false);
    $("#metric1 #selDSagg").val('Sum');
    $("#metric1 #selDStime").val('seconds');
    $("#metrictable #inputQuery").val('');
  };

  function queryGen( aPlotname )
  {
    var sQuery = 'GET json \n';
    //period
    if( $("#inputSDate").val().length === 0 )
    {
      $("#inputSDate").trigger('focus');
      return false;
    }
    sQuery += 'PERIOD ' + (($("#inputEDate").val().length !== 0)?($("#inputSDate").val() + ' TO ' + $("#inputEDate").val()) : $("#inputSDate").val()) + ' \n';

    // PLOT clause
    var sMTabLen = $("#metricContent").children().length;
    for( var i = 0 ; sMTabLen > i ; i++ )
    {
      var $sCxt = $("#metric" + (i+1));
      // metric
      var sMetric = $sCxt.find("#inputMetric");
      if( sMetric.val().length === 0 )
      {
        sMetric.trigger('focus');
        return false;
      }
      sQuery += ((i>0)?'\n UNION \n':'') + 'PLOT ' + sMetric.val();

      // tags
      var $sTags = $sCxt.find("#metrictags").children().filter("div");
      var isLB = 0, isRB = 0;

      for( var j = 0 ; $sTags.length > j ; j++ )
      {
        var sTagKey = $sTags.eq(j).find(".tagkey").val();
        var sTagVal = $sTags.eq(j).find(".tagval").val();
        if( (sTagKey.length !== 0) &&
            (sTagVal.length !== 0) )
        {
          if( isLB === 0 )
          {
            sQuery += ' {';
            isLB = 1;
          }
          else
          {
            sQuery += ',';
          }
          sQuery += sTagKey + '=' + sTagVal;
          if( j === $sTags.length-1 )
          {
            sQuery += '}';
            isRB = 1;
          }
        }
      }

      if( isLB === 1 && isRB === 0 )
      {
        sQuery += '}';
      }

      sQuery += ' \n    AGGREGATOR ' + $sCxt.find("#selAggregator").val().toUpperCase() +
        ($sCxt.find("#inputRate").is(":checked") ? ' OF RATE' : '');

      if( $sCxt.find("#inputDStime").val().length !== 0 )
      {
        sQuery += ' \n    DOWNSAMPLING ' + $sCxt.find("#inputDStime").val() + $sCxt.find("#selDStime").val()[0] +
          '-' + $sCxt.find("#selDSagg").val().toLowerCase();
      }
      if( $sCxt.find("#inputLegend").val().length !== 0 )
      {
        sQuery += ' \n    LEGEND "' + $sCxt.find("#inputLegend").val() + '"';
      }
    }
    sQuery += ';';

    if(aPlotname === '')
    {
      $("#inputQuery").val(sQuery);
    }
    else
    {
      gQueryMap[aPlotname] = [sQuery,null,null];
    }

    return true;
  };

    var treeData = [
    {title: "ClusterA", isFolder: true, key: "id1",
            children: [
            {title: "MySQL"},
            {title: "Oracle"}
          ]
    },
    {title: "ClusterB", isFolder: true, key: "id2",
          children: [
            {title: "System resource"},
            {title: "HDFS"},
            {title: "HBase"},
            {title: "MR"}
          ]
    },
    {title: "Some page"},
    {title: "Public pages", isFolder: true, key: "id3", hideCheckbox: true, tooltip: "Everyone can see!", unselectable: true,
          children: [
            {title: "Disk usage"},
            {title: "CPU load"}
          ]
  }];

  function initDtree()
  {
    $("#dtree").dynatree({
        children: treeData,
        checkbox: true,
  
            onSelect: function(select, node) {
            // Display list of selected nodes
            var selNodes = node.tree.getSelectedNodes();
            // convert to title/key array
            var selKeys = $.map(selNodes, function(node){
                    return "[" + node.data.key + "]: '" + node.data.title + "'";
            });
          var selTitles = $.map(selNodes, function(node){
                return "'" + node.data.title + "'";
          });
            $("#echoSelection2").text(selKeys.join(", "));

          $("#Delpage").unbind('click');
          $("#Delpage").bind('click', function(){
            if( $('#delPageModal').length === 0 )
            {
              $('body').children().last().after(gDeletePageHtml);
            }
            $('#DelPageOk').unbind('click');
            $('#DelPageOk').bind('click', function(){
              $('#delPageModal').modal('hide');
              delSelPages();
            });

            $("#delPagelist").replaceWith('<span id="delPagelist">Do you really want to DELETE' +
                '<ul style="margin:10px 0 10px 25px;">' +
                  '<li>'+ selTitles.join("</li><li>") + '</li>' +
                '</ul>' +
              'pages? </span>');

            $('#delPageModal').modal('toggle');
            
            function delSelPages() {
              for(var i=0; i < selNodes.length; i++)
              {
                selNodes[i].remove();
              }
            };
          });
            },
            onClick: function(node, event) {
            // We should not toggle, if target was "checkbox", because this
            // would result in double-toggle (i.e. no toggle)
            //if( node.getEventTargetType(event) == "title" )
            //        node.toggleSelect();
            },
            onDblClick: function(node, event) {
                if( node.getEventTargetType(event) == "title" )
                    node.toggleSelect();
            },
            onKeydown: function(node, event) {
            if( event.which == 32 ) {
                    node.toggleSelect();
                    return false;
            }
            },
            // The following options are only required, if we have more than one tree on one page:
            cookieId: "dynatree-Cb2",
            idPrefix: "dynatree-Cb2-",
        dnd: {
                onDragStart: function(node) {
                /** This function MUST be defined to enable dragging for the tree.
                 *  Return false to cancel dragging of node.
                 */
                logMsg("tree.onDragStart(%o)", node);
                return true;
                },
                onDragStop: function(node) {
                // This function is optional.
                logMsg("tree.onDragStop(%o)", node);
                },
                autoExpandMS: 1000,
                preventVoidMoves: true, // Prevent dropping nodes 'before self', etc.
                onDragEnter: function(node, sourceNode) {
                /** sourceNode may be null for non-dynatree droppables.
                 *  Return false to disallow dropping on node. In this case
                 *  onDragOver and onDragLeave are not called.
                 *  Return 'over', 'before, or 'after' to force a hitMode.
                 *  Return ['before', 'after'] to restrict available hitModes.
                 *  Any other return value will calc the hitMode from the cursor position.
                 */
                logMsg("tree.onDragEnter(%o, %o)", node, sourceNode);
                return true;
                },
                onDragOver: function(node, sourceNode, hitMode) {
                /** Return false to disallow dropping this node.
                 *
                 */
                logMsg("tree.onDragOver(%o, %o, %o)", node, sourceNode, hitMode);
                // Prevent dropping a parent below it's own child
                if(node.isDescendantOf(sourceNode)){
                        return false;
                }
                // Prohibit creating childs in non-folders (only sorting allowed)
                if( !node.data.isFolder && hitMode =='over' )
                        return false;
                },
                onDrop: function(node, sourceNode, hitMode, ui, draggable) {
                /** This function MUST be defined to enable dropping of items on
                 * the tree.
                 */
                logMsg("tree.onDrop(%o, %o, %s)", node, sourceNode, hitMode);
                sourceNode.move(node, hitMode);
                // expand the drop target
                //        sourceNode.expand(true);
                },
                onDragLeave: function(node, sourceNode) {
                /** Always called if onDragEnter was called.
                 */
                logMsg("tree.onDragLeave(%o, %o)", node, sourceNode);
                }
        }
    });
  };

  function initAllChart()
  {
    //chartwizard("chart1", "data-eu-gdp-growth-", 1000);
    //chartwizard("chart2", "data-eu-gdp-growth-", 1000);
    //chartwizard("chart3", "data-eu-gdp-growth-", 1000);
  }

  function chartwizard( aChartId, aUrl, aInterval )
  {
    var data = [];
    // fetch one series, adding to what we got
    var alreadyFetched = {};
    var originsel = $("#"+aChartId);
    var options = {
      legend: { show: true },
      lines: { show: true },
      points: { show: true },
      xaxis: { tickDecimals: 0, tickSize: 1 },
      selection: { mode: "xy" }
    };

    var zooming = $.plot("#zoomingchart", data, options);
    var origin = $.plot(originsel, data, options);

    var iteration = 0;

    function fetchData( ) 
    {
      iteration++;
      function onDataReceived(series) {
        // we get all the data in one go, if we only got partial
        // data, we could merge it with what we already got
        data = [ series ];
                
        origin = $.plot(originsel, data, options);
        //zooming = $.plot("#zoomingchart", data, options);
      }
        
      $.ajax({
        // usually, we'll just call the same URL, a script
        // connected to a database, but in this case we only
        // have static example files so we need to modify the
        // URL
        url: "/s/cis/mypage/" + aUrl + iteration + ".json",
        method: 'GET',
        dataType: 'json',
        success: onDataReceived
      });
            
      if (iteration < 5)
        setTimeout(fetchData, 1000);
      else {
        //data = [];
        //alreadyFetched = {};
      }
    }

    setTimeout(fetchData, aInterval);

    $("#zoomingchart").bind("plotselected", function (event, ranges) {

      // clamp the zooming to prevent eternal zoom
      if (ranges.xaxis.to - ranges.xaxis.from < 0.00001)
          ranges.xaxis.to = ranges.xaxis.from + 0.00001;
      if (ranges.yaxis.to - ranges.yaxis.from < 0.00001)
          ranges.yaxis.to = ranges.yaxis.from + 0.00001;
        
      // do the zooming
      zooming = $.plot($("#zoomingchart"), data, 
                      $.extend(true, {}, options, {
                          xaxis: { min: ranges.xaxis.from, max: ranges.xaxis.to },
                          yaxis: { min: ranges.yaxis.from, max: ranges.yaxis.to }
                      }));
        
      // don't fire event on the overview to prevent eternal loop
      //origin.setSelection(ranges, true);
    });

    $(originsel).bind("plotselected", function (event, ranges) {
      $('#zoomModal').modal('show');

      // clamp the zooming to prevent eternal zoom
      if (ranges.xaxis.to - ranges.xaxis.from < 0.00001)
          ranges.xaxis.to = ranges.xaxis.from + 0.00001;
      if (ranges.yaxis.to - ranges.yaxis.from < 0.00001)
          ranges.yaxis.to = ranges.yaxis.from + 0.00001;
        
      // do the zooming
      zooming = $.plot($("#zoomingchart"), data, 
                      $.extend(true, {}, options, {
                          xaxis: { min: ranges.xaxis.from, max: ranges.xaxis.to },
                          yaxis: { min: ranges.yaxis.from, max: ranges.yaxis.to }
                      }));
      origin.clearSelection(true);
    });
  };

  var gMetricTabModalHtml =
  "<div class=\"tab-pane fade\"> \
                    <div class=\"control-group\"> \
                      <label class=\"control-label\" for=\"inputMetric\"> \
                        <i style=\"color:red\">*&nbsp;</i>Metric \
                      </label> \
                      <div class=\"controls\"> \
                        <input class=\"input-medium\" type=\"text\" id=\"inputMetric\" placeholder=\"metric name\"> \
                      </div> \
                    </div> \
                    <div id=\"metrictags\" class=\"control-group\"> \
                      <label class=\"control-label\"> \
                        tag1 \
                      </label> \
                      <div class=\"controls\"> \
                        <input class=\"tagkey input-small\" type=\"text\" placeholder=\"tagKey\"> \
                        = \
                        <input class=\"tagval input-small\" type=\"text\" placeholder=\"tagValue\"> \
                      </div> \
                    </div> \
                    <div class=\"control-group\"> \
                      <label class=\"control-label\" for=\"inputAggregator\"> \
                        Aggregator \
                      </label> \
                      <div class=\"controls\"> \
                        <select id=\"selAggregator\"> \
                          <option>Sum</option> \
                          <option>Avg</option> \
                          <option>Min</option> \
                          <option>Max</option> \
                          <option>Dev</option> \
                        </select> \
                        <label class=\"checkbox\"> \
                          <input id=\"inputRate\" type=\"checkbox\"> Rate \
                        </label> \
                      </div> \
                    </div> \
                    <div class=\"control-group\"> \
                      <label class=\"control-label\" for=\"inputDS\"> \
                        Downsampling \
                      </label> \
                      <div class=\"controls\"> \
                        <select id=\"selDSagg\"> \
                          <option>Sum</option> \
                          <option>Avg</option> \
                          <option>Min</option> \
                          <option>Max</option> \
                          <option>Dev</option> \
                        </select> \
                        of \
                        <input class=\"input-mini\" type=\"text\" id=\"inputDStime\" placeholder=\"number\"> \
                        <select id=\"selDStime\"> \
                          <option>seconds</option> \
                          <option>minutes</option> \
                          <option>hours</option> \
                        </select> \
                      </div> \
                    </div> \
                    <div class=\"control-group\"> \
                      <label class=\"control-label\" for=\"inputLegend\"> \
                        Legend \
                      </label> \
                      <div class=\"controls\"> \
                        <input class=\"input-medium\" type=\"text\" id=\"inputLegend\" placeholder=\"Legend format\"> \
                      </div> \
                    </div> \
                  </div>";

  var gLoginModalHtml = 
'<div class="modal fade hide" id="loginModal" tabindex="-1" role="dialog" aria-hidden="true"> \
  <div class="modal-header"> \
    <button type="button" class="close">×</button> \
    <h4>Login</h4> \
  </div> \
  <div class="modal-body"> \
    <form class="form-horizontal"> \
      <div class="control-group"> \
        <label class="control-label" for="inputEmail">Email</label> \
        <div class="controls"> \
          <input type="text" id="inputEmail" placeholder="Email"> \
        </div> \
      </div> \
      <div class="control-group"> \
        <label class="control-label" for="inputPassword">Password</label> \
        <div class="controls"> \
          <input type="password" id="inputPassword" placeholder="Password"> \
        </div> \
      </div> \
      <div class="control-group" style="margin-bottom:0px;"> \
        <div class="controls"> \
          <label class="checkbox"> \
            <input type="checkbox"> Remember me \
          </label> \
          <button id="login" type="submit" class="btn btn-primary">Login</button> \
          <a id="signup" style="margin-left:30px;vertical-align:bottom;" href="#">sign up</a> \
        </div> \
      </div> \
    </form> \
  </div> \
</div>';

  var gDeletePageHtml =
  '<div class="modal hide fade" id="delPageModal"> \
  <div class="modal-header"> \
    <h4>Delete page</h4> \
  </div> \
  <div class="modal-body"> \
    <span id="delPagelist"></span> \
  </div> \
  <div class="modal-footer" style="text-align:center;padding:10px"> \
    <button id="DelPageOk" class="btn btn-primary">Yes</button> \
    <button class="btn" data-dismiss="modal" aria-hidden="true">No</button> \
  </div> \
</div>';

  var gAddPageHtml =
  '<div class="modal hide fade" id="addPageModal"> \
  <div class="modal-header"> \
    <h4></h4> \
  </div> \
  <div class="modal-body"> \
    <div class="control-group"> \
      <label class="control-label" for="inputPage">The name is&nbsp;</label> \
      <div class="controls" style="display:inline-block;"> \
        <input class="input-small" style="margin-bottom:0px;" type="text" id="inputPage" placeholder="name"></div> \
    </div> \
  </div> \
  <div class="modal-footer" style="text-align:center;padding:5px"> \
    <button id="AddPageOk" class="btn btn-primary">Add</button> \
    <button class="btn" data-dismiss="modal" aria-hidden="true">Cancel</button> \
  </div> \
</div>';

  var gChartWizHtml =
           '<li> \
              <div> \
                <table class="chartheader goodstate"> \
                  <tr> \
                    <td class="content"> \
                    </td> \
                    <td class="pull-right"> \
                      <div class="dropdown" style="display:inline-block;"> \
                        <a href="#" class="dropdown-toggle" data-toggle="dropdown"> \
                          <span class="iconnew-cog"></span> \
                        </a> \
                        <ul class="dropdown-menu alignLeft"> \
                          <li><a id="ddconfig" href="#">Configuration \
                          </a></li> \
                          <li><a id="dddisable" href="#">Disable \
                          </a></li> \
                          <li><a id="ddenable" href="#">Enable \
                          </a></li> \
                        </ul> \
                      </div> \
                      <a class="closechartbox" href="#"> \
                        <span class="iconnew-close"></span> \
                      </a> \
                    </td> \
                  </tr> \
                  <tr> \
                    <td class="title"> \
                    </td> \
                    <td class="greenhealth pull-right"> \
                      <p> \
                        <span class="icon16x16-greenHealth"></span> \
                        Good \
                      </p> \
                    </td> \
                  </tr> \
                  <tr class="updatetexttag"> \
                    <td id="updatetext"> \
                      Last updated <span class="ticker">0</span> seconds ago of <span class="freqt"></span>s cycle \
                    </td> \
                    <td id="taginfo" class="pull-right"> \
                    </td> \
                  </tr> \
                </table> \
                <div class="chartbody"> \
                  <table> \
                    <tr class="chartrow"> \
                      <td> \
                        <div id="chart" style="width:95%;height:95%;"> \
                        </div> \
                      </td> \
                    </tr> \
                    <tr class="charttext"> \
                      <td> \
                        <p> \
                          some description \
                        </p> \
                      </td> \
                    </tr> \
                  </table> \
                </div> \
              </div> \
            </li>';
})(jQuery);
