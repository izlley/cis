package com.skplanet.cisw.gwt.client;

import java.lang.Character;
import java.util.ArrayList;
import java.util.Date;
import java.util.Collections;
import java.util.HashMap;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.DomEvent;
import com.google.gwt.event.dom.client.ErrorEvent;
import com.google.gwt.event.dom.client.ErrorHandler;
import com.google.gwt.event.logical.shared.BeforeSelectionEvent;
import com.google.gwt.event.logical.shared.BeforeSelectionHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.http.client.URL;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.DecoratedTabPanel;
import com.google.gwt.user.client.ui.DecoratorPanel;
import com.google.gwt.user.client.ui.DisclosurePanel;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.InlineHTML;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.PushButton;
import com.google.gwt.user.client.ui.RadioButton;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.Tree;
import com.google.gwt.user.client.ui.HorizontalSplitPanel;
import com.google.gwt.user.client.ui.TreeItem;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.ValueBoxBase.TextAlignment;

/**
 * Root class for the 'query UI'. Manages the entire UI, forms to query the CISW
 * and other misc panels.
 */
public class QueryUi implements EntryPoint
{
    // Some URLs we use to fetch data from the CISW.
    private static final String AGGREGATORS_URL = "/aggregators";
    private static final String LOGS_URL = "/logs?json";
    private static final String STATS_URL = "/stats?json";
    private static final String VERSION_URL = "/version?json";
    private static final String GETFILE_URL = "/s/";
    private static final int    MYVIEW_COL_DIM = 2;

    private static final DateTimeFormat FULLDATE = DateTimeFormat
            .getFormat("yyyy/MM/dd-HH:mm:ss");

    private final Label current_error = new Label();

    private final DateTimeBox start_datebox = new DateTimeBox();
    private final DateTimeBox end_datebox = new DateTimeBox();
    private final CheckBox autoreload = new CheckBox("Autoreload");
    private final ValidatedTextBox autoreload_interval = new ValidatedTextBox();
    private Timer autoreload_timer;

    private final ValidatedTextBox yrange = new ValidatedTextBox();
    private final ValidatedTextBox y2range = new ValidatedTextBox();
    private final CheckBox ylog = new CheckBox();
    private final CheckBox y2log = new CheckBox();
    private final TextBox ylabel = new TextBox();
    private final TextBox y2label = new TextBox();
    private final ValidatedTextBox yformat = new ValidatedTextBox();
    private final ValidatedTextBox y2format = new ValidatedTextBox();
    private final ValidatedTextBox wxh = new ValidatedTextBox();

    private String keypos = ""; // Position of the key on the graph.
    private final CheckBox horizontalkey = new CheckBox("Horizontal layout");
    private final CheckBox keybox = new CheckBox("Box");
    private final CheckBox nokey = new CheckBox("No key (overrides others)");
    private boolean mIsOption = false;

    /**
     * Handles every change to the query form and gets a new graph. Whenever the
     * user changes one of the parameters of the graph, we want to automatically
     * get a new graph.
     */
    private final EventsHandler refreshgraph = new EventsHandler()
    {
        protected <H extends EventHandler> void onEvent(final DomEvent<H> event)
        {
            refreshGraph();
        }
    };

    /** List of known aggregation functions. Fetched once from the server. */
    private final ArrayList<String> aggregators = new ArrayList<String>();

    private final DecoratedTabPanel metrics = new DecoratedTabPanel();

    private final Image graph = new Image();
    private final Label graphstatus = new Label();
    /** Remember the last URI requested to avoid requesting twice the same. */
    private String lastgraphuri;

    /**
     * We only send one request at a time, how many have we not sent yet?. Note
     * that we don't buffer pending requests. When there are multiple ones
     * pending, we will only execute the last one and discard the other
     * intermediate ones, since the user is no longer interested in them.
     */
    private int pending_requests = 0;
    /** How many graph requests we make. */
    private int nrequests = 0;

    // Other misc panels.
    private final FlexTable logs = new FlexTable();
    private final HTML build_data = new HTML("Loading...");

    // query list for MyView panel
    private ArrayList<String> mMVQuerylist  = new ArrayList<String>();

    private final VerticalPanel mMVmainVP = new VerticalPanel();
    private final FlexTable mMVFTable = new FlexTable();
    private Timer mMVrefreshTimer;
    private final CheckBox mMVrefreshCB = new CheckBox("Autoreload");
    private final ValidatedTextBox mMVrefreshTextB = new ValidatedTextBox();
    
    private final TextArea mQueryTextA = new TextArea();

    private final EventsHandler myviewRefresh = new EventsHandler()
    {
        protected <H extends EventHandler> void onEvent(final DomEvent<H> event)
        {
            loadMyView( mvTree.getSelectedItem() );
        }
    };

    // Query list Map
    private final HashMap<String, ArrayList<String>> mMVQueryhash = new HashMap<String, ArrayList<String>>();
    
    
    // MV TreeItems
    final Tree mvTree = new Tree();
    final TreeItem sTmpviewItem = new TreeItem();
    
    /**
     * This is the entry point method.
     */
    public void onModuleLoad()
    {
        asyncGetJson(AGGREGATORS_URL, new GotJsonCallback()
        {
            public void got(final JSONValue json)
            {
                // Do we need more manual type checking? Not sure what will
                // happen
                // in the browser if something other than an array is returned.
                final JSONArray aggs = json.isArray();
                for (int i = 0; i < aggs.size(); i++)
                {
                    aggregators.add(aggs.get(i).isString().stringValue());
                }
                ((MetricForm) metrics.getWidget(0)).setAggregators(aggregators);
            }
        });

        // All UI elements need to regenerate the graph when changed.
        {
            final ValueChangeHandler<Date> vch = new ValueChangeHandler<Date>()
            {
                public void onValueChange(final ValueChangeEvent<Date> event)
                {
                    refreshGraph();
                }
            };
            TextBox tb = start_datebox.getTextBox();
            tb.addBlurHandler(refreshgraph);
            tb.addKeyPressHandler(refreshgraph);
            start_datebox.addValueChangeHandler(vch);
            tb = end_datebox.getTextBox();
            tb.addBlurHandler(refreshgraph);
            tb.addKeyPressHandler(refreshgraph);
            end_datebox.addValueChangeHandler(vch);
        }
        autoreload_interval.addBlurHandler(refreshgraph);
        autoreload_interval.addKeyPressHandler(refreshgraph);
        yrange.addBlurHandler(refreshgraph);
        yrange.addKeyPressHandler(refreshgraph);
        y2range.addBlurHandler(refreshgraph);
        y2range.addKeyPressHandler(refreshgraph);
        ylog.addClickHandler(new AdjustYRangeCheckOnClick(ylog, yrange));
        y2log.addClickHandler(new AdjustYRangeCheckOnClick(y2log, y2range));
        ylog.addClickHandler(refreshgraph);
        y2log.addClickHandler(refreshgraph);
        ylabel.addBlurHandler(refreshgraph);
        ylabel.addKeyPressHandler(refreshgraph);
        y2label.addBlurHandler(refreshgraph);
        y2label.addKeyPressHandler(refreshgraph);
        yformat.addBlurHandler(refreshgraph);
        yformat.addKeyPressHandler(refreshgraph);
        y2format.addBlurHandler(refreshgraph);
        y2format.addKeyPressHandler(refreshgraph);
        wxh.addBlurHandler(refreshgraph);
        wxh.addKeyPressHandler(refreshgraph);
        horizontalkey.addClickHandler(refreshgraph);
        keybox.addClickHandler(refreshgraph);
        nokey.addClickHandler(refreshgraph);

        yrange.setValidationRegexp("^(" // Nothing or
                + "|\\[([-+.0-9eE]+|\\*)?" // "[start
                + ":([-+.0-9eE]+|\\*)?\\])$"); // :end]"
        yrange.setVisibleLength(5);
        yrange.setMaxLength(44); // MAX=2^26=20 chars: "[-$MAX:$MAX]"
        yrange.setText("[0:]");

        y2range.setValidationRegexp("^(" // Nothing or
                + "|\\[([-+.0-9eE]+|\\*)?" // "[start
                + ":([-+.0-9eE]+|\\*)?\\])$"); // :end]"
        y2range.setVisibleLength(5);
        y2range.setMaxLength(44); // MAX=2^26=20 chars: "[-$MAX:$MAX]"
        y2range.setText("[0:]");
        y2range.setEnabled(false);
        y2log.setEnabled(false);

        ylabel.setVisibleLength(10);
        ylabel.setMaxLength(50); // Arbitrary limit.
        y2label.setVisibleLength(10);
        y2label.setMaxLength(50); // Arbitrary limit.
        y2label.setEnabled(false);

        yformat.setValidationRegexp("^(|.*%..*)$"); // Nothing or at least one
                                                    // %?
        yformat.setVisibleLength(10);
        yformat.setMaxLength(16); // Arbitrary limit.
        y2format.setValidationRegexp("^(|.*%..*)$"); // Nothing or at least one
                                                     // %?
        y2format.setVisibleLength(10);
        y2format.setMaxLength(16); // Arbitrary limit.
        y2format.setEnabled(false);

        wxh.setValidationRegexp("^[1-9][0-9]{2,}x[1-9][0-9]{2,}$"); // 100x100
        wxh.setVisibleLength(9);
        wxh.setMaxLength(11); // 99999x99999
        wxh.setText((Window.getClientWidth() - 20) + "x"
                + (Window.getClientHeight() * 4 / 5));

        final FlexTable table = new FlexTable();
        table.setText(0, 0, "From");
        {
            final HorizontalPanel hbox = new HorizontalPanel();
            hbox.add(new InlineLabel("To"));
            final Anchor now = new Anchor("(now)");
            now.addClickHandler(new ClickHandler()
            {
                public void onClick(final ClickEvent event)
                {
                    end_datebox.setValue(new Date());
                    refreshGraph();
                }
            });
            hbox.add(now);
            hbox.add(autoreload);
            hbox.setWidth("100%");
            table.setWidget(0, 1, hbox);
        }
        autoreload.addClickHandler(new ClickHandler()
        {
            public void onClick(final ClickEvent event)
            {
                if (autoreload.getValue())
                {
                    final HorizontalPanel hbox = new HorizontalPanel();
                    hbox.setWidth("100%");
                    hbox.add(new InlineLabel("Every:"));
                    hbox.add(autoreload_interval);
                    hbox.add(new InlineLabel("seconds"));
                    table.setWidget(1, 1, hbox);
                    if (autoreload_interval.getValue().isEmpty())
                    {
                        autoreload_interval.setValue("15");
                    }
                    autoreload_interval.setFocus(true);
                    lastgraphuri = ""; // Force refreshGraph.
                    refreshGraph(); // Trigger the 1st auto-reload
                }
                else
                {
                    table.setWidget(1, 1, end_datebox);
                }
            }
        });
        autoreload_interval.setValidationRegexp("^([5-9]|[1-9][0-9]+)$"); // >=5s
        autoreload_interval.setMaxLength(4);
        autoreload_interval.setVisibleLength(8);

        table.setWidget(1, 0, start_datebox);
        table.setWidget(1, 1, end_datebox);
        {
            final HorizontalPanel hbox = new HorizontalPanel();
            hbox.add(new InlineLabel("WxH:"));
            hbox.add(wxh);
            table.setWidget(0, 3, hbox);
        }
        {
            final MetricForm.MetricChangeHandler metric_change_handler = new MetricForm.MetricChangeHandler()
            {
                public void onMetricChange(final MetricForm metric)
                {
                    final int index = metrics.getWidgetIndex(metric);
                    metrics.getTabBar().setTabText(index, getTabTitle(metric));
                }

                private String getTabTitle(final MetricForm metric)
                {
                    final String metrictext = metric.getMetric();
                    final int last_period = metrictext.lastIndexOf('.');
                    if (last_period < 0)
                    {
                        return metrictext;
                    }
                    return metrictext.substring(last_period + 1);
                }
            };
            final EventsHandler updatey2range = new EventsHandler()
            {
                protected <H extends EventHandler> void onEvent(
                        final DomEvent<H> event)
                {
                    for (final Widget metric : metrics)
                    {
                        if (!(metric instanceof MetricForm))
                        {
                            continue;
                        }
                        if (((MetricForm) metric).x1y2().getValue())
                        {
                            y2range.setEnabled(true);
                            y2log.setEnabled(true);
                            y2label.setEnabled(true);
                            y2format.setEnabled(true);
                            return;
                        }
                    }
                    y2range.setEnabled(false);
                    y2log.setEnabled(false);
                    y2label.setEnabled(false);
                    y2format.setEnabled(false);
                }
            };
            final MetricForm metric = new MetricForm(refreshgraph);
            metric.x1y2().addClickHandler(updatey2range);
            metric.setMetricChangeHandler(metric_change_handler);
            metrics.add(metric, "metric 1");
            metrics.selectTab(0);
            metrics.add(new InlineLabel("Loading..."), "+");
            metrics.addBeforeSelectionHandler(new BeforeSelectionHandler<Integer>()
            {
                public void onBeforeSelection(
                        final BeforeSelectionEvent<Integer> event)
                {
                    final int item = event.getItem();
                    final int nitems = metrics.getWidgetCount();
                    if (item == nitems - 1)
                    { // Last item: the "+" was clicked.
                        event.cancel();
                        final MetricForm metric = new MetricForm(refreshgraph);
                        metric.x1y2().addClickHandler(updatey2range);
                        metric.setMetricChangeHandler(metric_change_handler);
                        metric.setAggregators(aggregators);
                        metrics.insert(metric, "metric " + nitems, item);
                        metrics.selectTab(item);
                        metric.setFocus(true);
                    }
                }
            });
            table.setWidget(2, 0, metrics);
        }
        table.getFlexCellFormatter().setColSpan(2, 0, 2);
        table.getFlexCellFormatter().setRowSpan(1, 3, 2);
        final DecoratedTabPanel optpanel = new DecoratedTabPanel();
        optpanel.add(makeAxesPanel(), "Axes");
        optpanel.add(makeKeyPanel(), "Key");
        optpanel.selectTab(0);
        table.setWidget(1, 3, optpanel);

        /*
         * Save Button
         */
        Button sSave = new Button("save", new ClickHandler() {
            public void onClick(ClickEvent event)
            {
                mMVQuerylist.add(lastgraphuri);
            }
        });
        table.setWidget(3, 0, sSave);
        
        /*
         * Input query explicitly
         */
        ///
        Grid advancedQuery = new Grid(2,1);
        advancedQuery.setCellSpacing(3);
        mQueryTextA.setVisibleLines(10);
        mQueryTextA.setWidth("500px");
        advancedQuery.setWidget(0, 0, mQueryTextA);
        
        Button sSend = new Button("Graph it!", new ClickHandler() {
            public void onClick(ClickEvent event)
            {
                doPlotwithQuery();
            }
        });
        advancedQuery.setWidget(1, 0, sSend);
        
        DisclosurePanel dpQuery = new DisclosurePanel("Query Input");
        dpQuery.setAnimationEnabled(true);
        dpQuery.setContent(advancedQuery);
        table.setWidget(3, 1, dpQuery);
        ///
        
        final DecoratorPanel decorator = new DecoratorPanel();
        decorator.setWidget(table);
        final VerticalPanel graphpanel = new VerticalPanel();
        graphpanel.add(decorator);
        {
            final VerticalPanel graphvbox = new VerticalPanel();
            graphvbox.add(graphstatus);
            graph.setVisible(false);
            graphvbox.add(graph);
            graph.addErrorHandler(new ErrorHandler()
            {
                public void onError(final ErrorEvent event)
                {
                    graphstatus.setText("Oops, failed to load the graph.");
                }
            });
            graphpanel.add(graphvbox);
        }

        /**********************************
         * MyView tabpanel
         **********************************/
        final DecoratedTabPanel tabpanel = new DecoratedTabPanel();
        tabpanel.setSize("100%", "");
        
        HorizontalSplitPanel hspMVpanel = new HorizontalSplitPanel();
        hspMVpanel.setSplitPosition("145px");
        hspMVpanel.setSize("100%", "800px");

        /**
         * MyView Left Tree
         */
        final VerticalPanel sMVleftTreeVP = new VerticalPanel();
        sMVleftTreeVP.setSpacing(10);
        
        /*
         * Buttons
         */
        HorizontalPanel sMVleftButtonHP = new HorizontalPanel();
        //sMVleftButtonHP.setSpacing(10);
        final Image sPlusImg = new Image();
        sPlusImg.setUrl( GETFILE_URL + "plus.gif" );
        
        PushButton sPlusButton = new PushButton( sPlusImg );
        sPlusButton.setSize("16px", "16px");
        sPlusButton.addClickHandler(new ClickHandler() {
            public void onClick(final ClickEvent event)
            {
                mvTree.addItem( new TreeItem("...") );
            }
        });

        sMVleftButtonHP.add( sPlusButton );
        sMVleftButtonHP.setCellHeight(sPlusButton, "16");
        sMVleftButtonHP.setCellWidth(sPlusButton, "16");
        sMVleftTreeVP.add( sMVleftButtonHP );
        sMVleftButtonHP.setSize("16", "16");
        
        /*
         * Tree
         */
        mvTree.setSize("100%", "");
        mvTree.setAnimationEnabled(true);
        sTmpviewItem.setText("TmpView");
        sTmpviewItem.setTitle("TmpView");
        
        mvTree.addItem(sTmpviewItem);
        
        final TreeItem trtmNewItem_1 = new TreeItem();
        trtmNewItem_1.setText("ClusterA_program");
        trtmNewItem_1.setTitle("ClusterA_program");
        mvTree.addItem(trtmNewItem_1);
        
        final TreeItem trtmNewItem_1_1 = new TreeItem();
        trtmNewItem_1_1.setText("Hadoop");
        trtmNewItem_1.addItem(trtmNewItem_1_1);
        
        final TreeItem trtmNewItem_1_2 = new TreeItem();
        trtmNewItem_1_2.setText("MR");
        trtmNewItem_1.addItem(trtmNewItem_1_2);
        
        final TreeItem trtmNewItem_1_3 = new TreeItem();
        trtmNewItem_1_3.setText("HBase");
        trtmNewItem_1.addItem(trtmNewItem_1_3);
        
        final TreeItem trtmNewItem_1_4 = new TreeItem();
        trtmNewItem_1_4.setText("Postino");
        trtmNewItem_1.addItem(trtmNewItem_1_4);
        
        final TreeItem trtmNewItem_1_5 = new TreeItem();
        trtmNewItem_1_5.setText("CIS");
        trtmNewItem_1.addItem(trtmNewItem_1_5);
        
        final TreeItem trtmNewItem_1_6 = new TreeItem();
        trtmNewItem_1_6.setText("Workflow");
        trtmNewItem_1.addItem(trtmNewItem_1_6);
        
        trtmNewItem_1.setState(false);
        
        final TreeItem trtmNewItem_2 = new TreeItem();
        trtmNewItem_2.setText("ClusterA_node");
        trtmNewItem_2.setTitle("ClusterA_node");
        mvTree.addItem(trtmNewItem_2);
        
        final TreeItem trtmNewItem_2_1 = new TreeItem();
        trtmNewItem_2_1.setText("spade208");
        trtmNewItem_2.addItem(trtmNewItem_2_1);
        
        final TreeItem trtmNewItem_2_2 = new TreeItem();
        trtmNewItem_2_2.setText("spade209");
        trtmNewItem_2.addItem(trtmNewItem_2_2);
        
        final TreeItem trtmNewItem_2_3 = new TreeItem();
        trtmNewItem_2_3.setText("spade210");
        trtmNewItem_2.addItem(trtmNewItem_2_3);
        
        final TreeItem trtmNewItem_2_4 = new TreeItem();
        trtmNewItem_2_4.setText("spade211");
        trtmNewItem_2.addItem(trtmNewItem_2_4);
        
        final TreeItem trtmNewItem_2_5 = new TreeItem();
        trtmNewItem_2_5.setText("spade212");
        trtmNewItem_2.addItem(trtmNewItem_2_5);
        
        trtmNewItem_2.setState(false);
        
        final TreeItem trtmNewItem_3 = new TreeItem();
        trtmNewItem_3.setText("ClusterA_overview");
        trtmNewItem_3.setTitle("ClusterA_overview");
        mvTree.addItem(trtmNewItem_3);
        
        final TreeItem trtmNewItem_3_1 = new TreeItem();
        trtmNewItem_3_1.setText("CPU");
        trtmNewItem_3.addItem(trtmNewItem_3_1);
        
        final TreeItem trtmNewItem_3_2 = new TreeItem();
        trtmNewItem_3_2.setText("DISK");
        trtmNewItem_3.addItem(trtmNewItem_3_2);
        
        final TreeItem trtmNewItem_3_3 = new TreeItem();
        trtmNewItem_3_3.setText("NETWORK");
        trtmNewItem_3.addItem(trtmNewItem_3_3);
        
        trtmNewItem_3.setState(false);
        
        final TreeItem trtmNewItem_4 = new TreeItem();
        trtmNewItem_4.setText("Leejy");
        trtmNewItem_4.setTitle("Leejy");
        mvTree.addItem(trtmNewItem_4);
        
        mvTree.addSelectionHandler(new SelectionHandler<TreeItem>()
        {
            public void onSelection(final SelectionEvent<TreeItem> event)
            {
                TreeItem item = event.getSelectedItem();
                loadMyView( item );
            }
        });
        sMVleftTreeVP.add(mvTree);
        
        hspMVpanel.setLeftWidget(sMVleftTreeVP);

        /*
         * MyView left menu
         */
        
        /*
         * MyView menu : Autoload
         */
        final HorizontalPanel sMVmenuHP = new HorizontalPanel();
        sMVmenuHP.setSpacing(5);
        
        ///
        Grid advancedOptions = new Grid(2, 5);
        advancedOptions.setCellSpacing(3);
        advancedOptions.setWidget(0, 0, mMVrefreshCB);
        advancedOptions.setHTML(0, 1, "<i>(&nbsp;Every</i>");
        mMVrefreshTextB.setWidth("50");
        advancedOptions.setWidget(0, 2, mMVrefreshTextB);
        advancedOptions.setHTML(0, 3, "<i>seconds&nbsp;)</i>");
        
        DisclosurePanel advancedDisclosure = new DisclosurePanel("Options");
        advancedDisclosure.setAnimationEnabled(true);
        advancedDisclosure.setContent(advancedOptions);
        ///
        final DecoratorPanel sMVupsideDeco = new DecoratorPanel();
        sMVupsideDeco.setWidget(advancedDisclosure);
        sMVmenuHP.add(sMVupsideDeco);

        final FlowPanel sFP = new FlowPanel();
        //final DecoratorPanel sMVupsideDeco1 = new DecoratorPanel();
        
        //sFP.add(sMVupsideDeco1);
        
        // FlexTable 1
        final FlexTable sFT = new FlexTable();
        sFP.add(sFT);
        FlexCellFormatter cellFormatter = sFT.getFlexCellFormatter();
        sFT.addStyleName("cw-FlexTable");

        cellFormatter.setColSpan(0, 0, 3);
        //sFT.setWidget(0, 0, mMVrefreshCB );
        //sFT.setWidget(1, 0, new InlineLabel("(" + " " + "Every") );
        //mMVrefreshTextB.setPixelSize(35, 15);
        //sFT.setWidget(1, 1, mMVrefreshTextB );
        //sFT.setWidget(1, 2, new InlineLabel("seconds" + " " + ")") );

        // Add some text
        cellFormatter.setHorizontalAlignment(
            0, 1, HasHorizontalAlignment.ALIGN_LEFT);

        //sMVupsideDeco1.setWidget(sFT);

        if (mMVrefreshTextB.getValue().isEmpty())
        {
            mMVrefreshTextB.setValue("15");
        }
        
        mMVrefreshCB.addClickHandler(new ClickHandler()
        {
            public void onClick(final ClickEvent event)
            {
                if (mMVrefreshCB.getValue())
                {
                    mMVrefreshTextB.setFocus(true);
                    loadMyView( mvTree.getSelectedItem() ); // Trigger the 1st auto-reload
                }
            }
        });
        mMVrefreshTextB.setValidationRegexp("^([5-9]|[1-9][0-9]+)$"); // >=5s
        mMVrefreshTextB.setMaxLength(4);
        mMVrefreshTextB.setVisibleLength(8);
        mMVrefreshTextB.addBlurHandler(refreshgraph);
        mMVrefreshTextB.addKeyPressHandler(refreshgraph);

        /*
         * MyView menu : Save on [ ]
         */
        //final DecoratorPanel sMVupsideDeco2 = new DecoratorPanel();
        final VerticalPanel sMVsaveVP = new VerticalPanel();
        sMVsaveVP.setSpacing(1);
        //sMVupsideDeco2.setWidget(sMVsaveVP);
        
        final ListBox sMVsaveDB = new ListBox(false);
        sMVsaveDB.ensureDebugId( "mvmListBox-dropBox" );

        for( int i = 1 ; mvTree.getItemCount() > i ; i++ )
        {
            final TreeItem sTitem1 = mvTree.getItem(i);
            if( sTitem1.getChildCount() == 0 )
            {
                sMVsaveDB.addItem( sTitem1.getText() );
                continue;
            }
            for( int j = 0 ; sTitem1.getChildCount() > j ; j++ )
            {
                final TreeItem sTitem2 = sTitem1.getChild(j);
                sMVsaveDB.addItem( sTitem1.getText() + "::" + sTitem2.getText() );
            }
        }
        
        Button sSaveButton = new Button("Save", new ClickHandler() {
            public void onClick(ClickEvent event)
            {
                final String sName;
                sName = sMVsaveDB.getItemText( sMVsaveDB.getSelectedIndex() );

                if( sName != null )
                {
                    setQueryListformHash( sName, mMVQuerylist );
                }
            }
        });
        
        sMVsaveVP.add( sSaveButton );
        sSaveButton.setSize("45", "23");
        //sMVsaveVP.add( new InlineLabel("Save on") );
        sMVsaveVP.add( sMVsaveDB );
        //sFP.add(sMVupsideDeco2);
        sFP.add(sMVsaveVP);

        /*
        sMVsaveDB.addClickHandler(new ClickHandler()
        {
            public void onClick(final ClickEvent event)
            {
                final String sName;
                sName = sMVsaveDB.getItemText( sMVsaveDB.getSelectedIndex() );

                if( sName != null )
                {
                    setQueryListformHash( sName, mMVQuerylist );
                }
                sMVsaveDB.setFocus(false);
            }
        });*/
        
        /*
         * MyView menu : Clear Button
         */
        //final DecoratorPanel sMVupsideDeco3 = new DecoratorPanel();
        Button sClearButton = new Button("Clear", new ClickHandler() {
            public void onClick(ClickEvent event)
            {
                final TreeItem sSelitem = mvTree.getSelectedItem();
                TreeItem sPitem = null;
                
                if( sSelitem == null )
                {
                    return;
                }
                if( sSelitem.getText().compareTo("TmpView") == 0 )
                {
                    mMVQuerylist.clear();
                }
                else
                {
                    if( ( sPitem = sSelitem.getParentItem() ) != null )
                    {
                        delQueryListformHash( sPitem.getText() + "::" + sSelitem.getText() );
                    }
                    else
                    {
                        delQueryListformHash( sSelitem.getText() );
                    }
                }
                
                mMVFTable.setBorderWidth(0);
                mMVFTable.removeAllRows();
            }
        });

        //sMVupsideDeco3.setWidget(sClearButton);
        //sFP.add(sMVupsideDeco3);
        sFP.add(sClearButton);
        sClearButton.setSize("45", "23");

        mMVmainVP.add(sMVmenuHP);
        //mMVmainVP.add(sFP);
        mMVmainVP.setCellHeight(sFP, "100");
        mMVmainVP.setCellWidth(sFP, "55");
        sFP.setSize("100%", "60px");
        mMVmainVP.add(mMVFTable);
        mMVmainVP.setSize("600px", "100%");

        hspMVpanel.setRightWidget(mMVmainVP);

        /**
         * Config Tab
         */
        final FlexTable sConfigFT = new FlexTable();
        
        /**
         * Alert Tab
         */
        final FlexTable sAlertFT = new FlexTable();
        
        tabpanel.add(hspMVpanel, "MyView", false);
        tabpanel.add(graphpanel, "Plot");
        tabpanel.add(new InlineLabel("Under construction, see u in Prototype3 !!"), "Config");
        tabpanel.add(new InlineLabel("Under construction, see u in Prototype3 !!"), "Alert");
        tabpanel.add(logs, "Logs");
        //tabpanel.add(build_data, "Version");
        tabpanel.add(new InlineLabel("CISW Prototype 2st !!"), "Version");
        tabpanel.selectTab(0);
        tabpanel.addBeforeSelectionHandler(new BeforeSelectionHandler<Integer>()
                {
                    public void onBeforeSelection(
                            final BeforeSelectionEvent<Integer> event)
                    {
                        clearError();
                        final int item = event.getItem();
                        switch (item)
                        {
                            //Main
                            case 0:
                                mvTree.setSelectedItem(sTmpviewItem, true);
                                //loadMyView( sTmpviewItem );
                                return;
                            //Plot
                            case 1:
                                return;
                            //Config
                            case 2:
                                return;
                            //Alert
                            case 3:
                                return;
                            //Logs
                            case 4:
                                refreshLogs();
                                return;
                            //Version
                            case 5:
                                //refreshVersion();
                                return;
                        }
                    }
                });
        final VerticalPanel root = new VerticalPanel();
        root.setWidth("100%");
        root.add(current_error);
        current_error.setVisible(false);
        current_error.addStyleName("dateBoxFormatError");
        root.add(tabpanel);
        RootPanel rootPanel = RootPanel.get("queryuimain");
        rootPanel.setWidth("100%");
        rootPanel.add(root);
        // Must be done at the end, once all the widgets are attached.
        ensureSameWidgetSize(optpanel);
    }

    /**
     * Builds the panel containing customizations for the axes of the graph.
     */
    private Grid makeAxesPanel()
    {
        final Grid grid = new Grid(5, 3);
        grid.setText(0, 1, "Y");
        grid.setText(0, 2, "Y2");
        setTextAlignCenter(grid.getRowFormatter().getElement(0));
        grid.setText(1, 0, "Label");
        grid.setWidget(1, 1, ylabel);
        grid.setWidget(1, 2, y2label);
        grid.setText(2, 0, "Format");
        grid.setWidget(2, 1, yformat);
        grid.setWidget(2, 2, y2format);
        grid.setText(3, 0, "Range");
        grid.setWidget(3, 1, yrange);
        grid.setWidget(3, 2, y2range);
        grid.setText(4, 0, "Log scale");
        grid.setWidget(4, 1, ylog);
        grid.setWidget(4, 2, y2log);
        setTextAlignCenter(grid.getCellFormatter().getElement(4, 1));
        setTextAlignCenter(grid.getCellFormatter().getElement(4, 2));
        return grid;
    }

    /**
     * Small helper to build a radio button used to change the position of the
     * key of the graph.
     */
    private RadioButton addKeyRadioButton(final Grid grid, final int row,
            final int col, final String pos)
    {
        final RadioButton rb = new RadioButton("keypos");
        rb.addClickHandler(new ClickHandler()
        {
            public void onClick(final ClickEvent event)
            {
                keypos = pos;
            }
        });
        rb.addClickHandler(refreshgraph);
        grid.setWidget(row, col, rb);
        return rb;
    }

    /**
     * Builds the panel containing the customizations for the key of the graph.
     */
    private Widget makeKeyPanel()
    {
        final Grid grid = new Grid(5, 5);
        addKeyRadioButton(grid, 0, 0, "out,left-top");
        addKeyRadioButton(grid, 0, 2, "out,center-top");
        addKeyRadioButton(grid, 0, 4, "out,right-top");
        addKeyRadioButton(grid, 1, 1, "top-left");
        addKeyRadioButton(grid, 1, 2, "top-center");
        addKeyRadioButton(grid, 1, 3, "top-right").setValue(true);
        addKeyRadioButton(grid, 2, 0, "out,center-left");
        addKeyRadioButton(grid, 2, 1, "center-left");
        addKeyRadioButton(grid, 2, 2, "center");
        addKeyRadioButton(grid, 2, 3, "center-right");
        addKeyRadioButton(grid, 2, 4, "out,center-right");
        addKeyRadioButton(grid, 3, 1, "bottom-left");
        addKeyRadioButton(grid, 3, 2, "bottom-center");
        addKeyRadioButton(grid, 3, 3, "bottom-right");
        addKeyRadioButton(grid, 4, 0, "out,bottom-left");
        addKeyRadioButton(grid, 4, 2, "out,bottom-center");
        addKeyRadioButton(grid, 4, 4, "out,bottom-right");
        final Grid.CellFormatter cf = grid.getCellFormatter();
        cf.getElement(1, 1).getStyle()
                .setProperty("borderLeft", "1px solid #000");
        cf.getElement(1, 1).getStyle()
                .setProperty("borderTop", "1px solid #000");
        cf.getElement(1, 2).getStyle()
                .setProperty("borderTop", "1px solid #000");
        cf.getElement(1, 3).getStyle()
                .setProperty("borderTop", "1px solid #000");
        cf.getElement(1, 3).getStyle()
                .setProperty("borderRight", "1px solid #000");
        cf.getElement(2, 1).getStyle()
                .setProperty("borderLeft", "1px solid #000");
        cf.getElement(2, 3).getStyle()
                .setProperty("borderRight", "1px solid #000");
        cf.getElement(3, 1).getStyle()
                .setProperty("borderLeft", "1px solid #000");
        cf.getElement(3, 1).getStyle()
                .setProperty("borderBottom", "1px solid #000");
        cf.getElement(3, 2).getStyle()
                .setProperty("borderBottom", "1px solid #000");
        cf.getElement(3, 3).getStyle()
                .setProperty("borderBottom", "1px solid #000");
        cf.getElement(3, 3).getStyle()
                .setProperty("borderRight", "1px solid #000");
        final VerticalPanel vbox = new VerticalPanel();
        vbox.add(new InlineLabel("Key location:"));
        vbox.add(grid);
        vbox.add(horizontalkey);
        keybox.setValue(true);
        vbox.add(keybox);
        vbox.add(nokey);
        return vbox;
    }

    private void refreshVersion()
    {
        asyncGetJson(VERSION_URL, new GotJsonCallback()
        {
            public void got(final JSONValue json)
            {
                final JSONObject bd = json.isObject();
                final JSONString shortrev = bd.get("short_revision").isString();
                final JSONString status = bd.get("repo_status").isString();
                final JSONNumber stamp = bd.get("timestamp").isNumber();
                final JSONString user = bd.get("user").isString();
                final JSONString host = bd.get("host").isString();
                final JSONString repo = bd.get("repo").isString();
                build_data.setHTML("CIS built from revision "
                        + shortrev.stringValue() + " in a "
                        + status.stringValue() + " state<br/>" + "Built on "
                        + new Date((long) (stamp.doubleValue() * 1000))
                        + " by " + user.stringValue() + '@'
                        + host.stringValue() + ':' + repo.stringValue());
            }
        });
    }

    private void refreshLogs()
    {
        asyncGetJson(LOGS_URL, new GotJsonCallback()
        {
            public void got(final JSONValue json)
            {
                final JSONArray logmsgs = json.isArray();
                final int nmsgs = logmsgs.size();
                final FlexTable.FlexCellFormatter fcf = logs
                        .getFlexCellFormatter();
                final FlexTable.RowFormatter rf = logs.getRowFormatter();
                for (int i = 0; i < nmsgs; i++)
                {
                    final String msg = logmsgs.get(i).isString().stringValue();
                    String part = msg.substring(0, msg.indexOf('\t'));
                    logs.setText(i * 2, 0, new Date(
                            Integer.valueOf(part) * 1000L).toString());
                    logs.setText(i * 2 + 1, 0, ""); // So we can change the
                                                    // style ahead.
                    int pos = part.length() + 1;
                    part = msg.substring(pos, msg.indexOf('\t', pos));
                    if ("WARN".equals(part))
                    {
                        rf.getElement(i * 2).getStyle()
                                .setBackgroundColor("#FCC");
                        rf.getElement(i * 2 + 1).getStyle()
                                .setBackgroundColor("#FCC");
                    }
                    else if ("ERROR".equals(part))
                    {
                        rf.getElement(i * 2).getStyle()
                                .setBackgroundColor("#F99");
                        rf.getElement(i * 2 + 1).getStyle()
                                .setBackgroundColor("#F99");
                    }
                    else
                    {
                        rf.getElement(i * 2).getStyle().clearBackgroundColor();
                        rf.getElement(i * 2 + 1).getStyle()
                                .clearBackgroundColor();
                        if ((i % 2) == 0)
                        {
                            rf.addStyleName(i * 2, "subg");
                            rf.addStyleName(i * 2 + 1, "subg");
                        }
                    }
                    pos += part.length() + 1;
                    logs.setText(i * 2, 1, part); // level
                    part = msg.substring(pos, msg.indexOf('\t', pos));
                    pos += part.length() + 1;
                    logs.setText(i * 2, 2, part); // thread
                    part = msg.substring(pos, msg.indexOf('\t', pos));
                    pos += part.length() + 1;
                    if (part.startsWith("com.skplanet."))
                    {
                        part = part.substring(13);
                    }
                    else if (part.startsWith("org.hbase."))
                    {
                        part = part.substring(10);
                    }
                    logs.setText(i * 2, 3, part); // logger
                    logs.setText(i * 2 + 1, 0, msg.substring(pos)); // message
                    fcf.setColSpan(i * 2 + 1, 0, 4);
                    rf.addStyleName(i * 2, "fwf");
                    rf.addStyleName(i * 2 + 1, "fwf");
                }
            }
        });
    }
    
    private void addLabels(final StringBuilder aReqData)
    {
        final String ylabel = this.ylabel.getText();
        if (!ylabel.isEmpty())
        {
            if( mIsOption != true )
            {
                aReqData.append(" OPTION ylabel=").append(URL.encodeComponent(ylabel));
                mIsOption = true;
            }
            else
            {
                aReqData.append(" ylabel=").append(URL.encodeComponent(ylabel));
            }
        }
        if (y2label.isEnabled())
        {
            final String y2label = this.y2label.getText();
            if (!y2label.isEmpty())
            {
                aReqData.append(" y2label=").append(URL.encodeComponent(y2label));
            }
        }
    }

    private void addFormats(final StringBuilder aReqData)
    {
        final String yformat = this.yformat.getText();
        if (!yformat.isEmpty())
        {
            if( mIsOption != true )
            {
                aReqData.append(" OPTION yformat=").append(URL.encodeComponent(yformat));
                mIsOption = true;
            }
            else
            {
                aReqData.append(" yformat=").append(URL.encodeComponent(yformat));
            }
        }
        if (y2format.isEnabled())
        {
            final String y2format = this.y2format.getText();
            if (!y2format.isEmpty())
            {
                aReqData.append(" y2format=").append(URL.encodeComponent(y2format));
            }
        }
    }

    private void addYRanges(final StringBuilder aReqData)
    {
        final String yrange = this.yrange.getText();
        if (!yrange.isEmpty())
        {
            if( mIsOption != true )
            {
                aReqData.append(" OPTION yrange=").append(yrange);
                mIsOption = true;
            }
            else
            {
                aReqData.append(" yrange=").append(yrange);
            }
        }
        if (y2range.isEnabled())
        {
            final String y2range = this.y2range.getText();
            if (!y2range.isEmpty())
            {
                aReqData.append(" y2range=").append(y2range);
            }
        }
    }

    private void addLogscales(final StringBuilder aReqData)
    {
        if (ylog.getValue())
        {
            if( mIsOption != true )
            {
                aReqData.append(" OPTION ylog");
                mIsOption = true;
            }
            else
            {
                aReqData.append(" ylog");
            }
        }
        if (y2log.isEnabled() && y2log.getValue())
        {
            aReqData.append(" y2log");
        }
    }

    private void refreshGraph()
    {
        boolean sAddIgnore = false;
        final Date start = start_datebox.getValue();
        if (start == null)
        {
            graphstatus.setText("Please specify a start time.");
            return;
        }
        final Date end = end_datebox.getValue();
        if (end != null && !autoreload.getValue())
        {
            if (end.getTime() <= start.getTime())
            {
                end_datebox.addStyleName("dateBoxFormatError");
                graphstatus.setText("End time must be after start time!");
                return;
            }
        }
        final String sURL = "/q?";
        final StringBuilder sQuery = new StringBuilder();
        sQuery.append( "PERIOD " ).append(FULLDATE.format(start));
        if (end != null && !autoreload.getValue())
        {
            sQuery.append(" TO ").append(FULLDATE.format(end));
        }
        else
        {
            // If there's no end-time, the graph may change while the URL remains
            // the same.  No browser seems to re-fetch an image once it's been
            // fetched, even if we destroy the DOM object and re-created it with the
            // same src attribute.  This has nothing to do with caching headers sent
            // by the server.  The browsers simply won't retrieve the same URL again
            // through JavaScript manipulations, period.  So as a workaround, we add
            // a special parameter that the server will delete from the query.
            sAddIgnore = true;
          }

        if (!addAllMetrics(sQuery))
        {
            return;
        }
        mIsOption = false;
        addLabels(sQuery);
        addFormats(sQuery);
        addYRanges(sQuery);
        addLogscales(sQuery);
        if (nokey.getValue())
        {
            if( mIsOption != true )
            {
                sQuery.append(" OPTION nokey");
                mIsOption = true;
            }
            else
            {
                sQuery.append(" nokey");
            }
        }
        else if (!keypos.isEmpty() || horizontalkey.getValue())
        {
            if( mIsOption != true )
            {
                sQuery.append(" OPTION key=(");
                mIsOption = true;
            }
            else
            {
                sQuery.append(" key=(");
            }
            if (!keypos.isEmpty())
            {
                sQuery.append(keypos);
            }
            if (horizontalkey.getValue())
            {
                sQuery.append(",horiz");
            }
            if (keybox.getValue())
            {
                sQuery.append(",box");
            }
            sQuery.append(")");
        }
        if( mIsOption != true )
        {
            sQuery.append(" OPTION wxh=").append(wxh.getText());
            mIsOption = true;
        }
        else
        {
            sQuery.append(" wxh=").append(wxh.getText());
        }
        mIsOption = false;
        
        if( sAddIgnore == true )
        {
            sQuery.append( " IGNORE " + (nrequests++) );
        }
        sQuery.append( ";" );

        final String sQstr = sQuery.toString();
        if (sQuery.equals(lastgraphuri))
        {
            return; // Don't re-request the same graph.
        }
        else if (pending_requests++ > 0)
        {
            return;
        }
        lastgraphuri = sQstr;
        graphstatus.setText("Loading graph...");
        asyncGetJson( sURL + "GET json " + sQstr, new GotJsonCallback()
        {
            //public void got(final JSONValue json)
            public void got(final JSONValue json)
            {
                if (autoreload_timer != null)
                {
                    autoreload_timer.cancel();
                    autoreload_timer = null;
                }
                final JSONObject result = json.isObject();
                final JSONValue err = result.get("err");
                String msg = "";
                if (err != null)
                {
                    displayError("An error occurred while generating the graph: "
                            + err.isString().stringValue());
                    graphstatus.setText("Please correct the error above.");
                }
                else
                {
                    clearError();
                    final JSONValue nplotted = result.get("plotted");
                    final JSONValue cachehit = result.get("cachehit");
                    if (cachehit != null)
                    {
                        msg += "Cache hit ("
                                + cachehit.isString().stringValue() + "). ";
                    }
                    if (nplotted != null
                            && nplotted.isNumber().doubleValue() > 0)
                    {
                        graph.setUrl(sURL + "GET png " + sQstr);
                        graph.setVisible(true);
                        //graph.
                        msg += result.get("points").isNumber()
                                + " points retrieved, " + nplotted
                                + " points plotted";
                    }
                    else
                    {
                        graph.setVisible(false);
                        msg += "Your query didn't return anything";
                    }
                    final JSONValue timing = result.get("timing");
                    if (timing != null)
                    {
                        msg += " in " + timing + "ms.";
                    }
                    else
                    {
                        msg += '.';
                    }
                }
                final JSONValue info = result.get("info");
                if (info != null)
                {
                    if (!msg.isEmpty())
                    {
                        msg += ' ';
                    }
                    msg += info.isString().stringValue();
                }
                graphstatus.setText(msg);
                if (result.get("etags") != null)
                {
                    final JSONArray etags = result.get("etags").isArray();
                    final int netags = etags.size();
                    for (int i = 0; i < netags; i++)
                    {
                        if (i >= metrics.getWidgetCount())
                        {
                            break;
                        }
                        final Widget widget = metrics.getWidget(i);
                        if (!(widget instanceof MetricForm))
                        {
                            break;
                        }
                        final MetricForm metric = (MetricForm) widget;
                        final JSONArray tags = etags.get(i).isArray();
                        final int ntags = tags.size();
                        for (int j = 0; j < ntags; j++)
                        {
                            metric.autoSuggestTag(tags.get(j).isString()
                                    .stringValue());
                        }
                    }
                }
                if (autoreload.getValue())
                {
                    final int reload_in = Integer.parseInt(autoreload_interval
                            .getValue());
                    if (reload_in >= 5)
                    {
                        autoreload_timer = new Timer()
                        {
                            public void run()
                            {
                                // Verify that we still want auto reload and
                                // that the graph
                                // hasn't been updated in the mean time.
                                if (autoreload.getValue()
                                        && lastgraphuri == sQstr)
                                {
                                    // Force refreshGraph to believe that we
                                    // want a new graph.
                                    lastgraphuri = "";
                                    refreshGraph();
                                }
                            }
                        };
                        autoreload_timer.schedule(reload_in * 1000);
                    }
                }
                if (--pending_requests > 0)
                {
                    pending_requests = 0;
                    refreshGraph();
                }
            }
        });
    }

    private void doPlotwithQuery()
    {
        final String sURL = "/q?";
        final String sQstrOri = mQueryTextA.getText().trim();
        final StringBuilder sQuery = new StringBuilder();

        //
        int  i, j;
        char sCh;
        for( i = 0 ; sQstrOri.length() > i ; i++ )
        {
            switch( sCh = sQstrOri.charAt(i) )
            {
                case '+':
                    sQuery.append("%2B");
                    break;
                case '\n':
                case '\t':
                    sQuery.append(' ');
                    break;
                default:
                    sQuery.append(sCh);
            }
        }
        
        /* delete "GET <type>" */
        for( i = 0, j = 0 ; (sQuery.length() > i) && (j < 2) ; i++ )
        {
            if( (sQuery.charAt(i)  == ' ')  || 
                (sQuery.charAt(i)  == '\t') ||
                (sQuery.charAt(i)  == '\n') )
            {
                for( i++ ;
                      (sQuery.charAt(i)  == ' ')  || 
                      (sQuery.charAt(i)  == '\t') ||
                      (sQuery.charAt(i)  == '\n') ;
                      i++ ) {}
                i--;
                j++; // num of whitespaces
            }
        }
        sQuery.delete(0, i);
        //
        
        final String sQstr = sQuery.toString();

        /*if (pending_requests++ > 0)
        {
            return;
        }*/
        
        lastgraphuri = sQstr;
        graphstatus.setText("Loading graph...");
        asyncGetJson( sURL + "GET json " + sQstr, new GotJsonCallback()
        {
            //public void got(final JSONValue json)
            public void got(final JSONValue json)
            {
                if (autoreload_timer != null)
                {
                    autoreload_timer.cancel();
                    autoreload_timer = null;
                }
                final JSONObject result = json.isObject();
                final JSONValue err = result.get("err");
                String msg = "";
                if (err != null)
                {
                    displayError("An error occurred while generating the graph: "
                            + err.isString().stringValue());
                    graphstatus.setText("Please correct the error above.");
                }
                else
                {
                    clearError();
                    final JSONValue nplotted = result.get("plotted");
                    final JSONValue cachehit = result.get("cachehit");
                    if (cachehit != null)
                    {
                        msg += "Cache hit ("
                                + cachehit.isString().stringValue() + "). ";
                    }
                    if (nplotted != null
                            && nplotted.isNumber().doubleValue() > 0)
                    {
                        graph.setUrl(sURL + "GET png " + sQstr);
                        graph.setVisible(true);
                        //graph.
                        msg += result.get("points").isNumber()
                                + " points retrieved, " + nplotted
                                + " points plotted";
                    }
                    else
                    {
                        graph.setVisible(false);
                        msg += "Your query didn't return anything";
                    }
                    final JSONValue timing = result.get("timing");
                    if (timing != null)
                    {
                        msg += " in " + timing + "ms.";
                    }
                    else
                    {
                        msg += '.';
                    }
                }
                final JSONValue info = result.get("info");
                if (info != null)
                {
                    if (!msg.isEmpty())
                    {
                        msg += ' ';
                    }
                    msg += info.isString().stringValue();
                }
                graphstatus.setText(msg);
                if (result.get("etags") != null)
                {
                    final JSONArray etags = result.get("etags").isArray();
                    final int netags = etags.size();
                    for (int i = 0; i < netags; i++)
                    {
                        if (i >= metrics.getWidgetCount())
                        {
                            break;
                        }
                        final Widget widget = metrics.getWidget(i);
                        if (!(widget instanceof MetricForm))
                        {
                            break;
                        }
                        final MetricForm metric = (MetricForm) widget;
                        final JSONArray tags = etags.get(i).isArray();
                        final int ntags = tags.size();
                        for (int j = 0; j < ntags; j++)
                        {
                            metric.autoSuggestTag(tags.get(j).isString()
                                    .stringValue());
                        }
                    }
                }
                if (autoreload.getValue())
                {
                    final int reload_in = Integer.parseInt(autoreload_interval
                            .getValue());
                    if (reload_in >= 5)
                    {
                        autoreload_timer = new Timer()
                        {
                            public void run()
                            {
                                // Verify that we still want auto reload and
                                // that the graph
                                // hasn't been updated in the mean time.
                                if (autoreload.getValue()
                                        && lastgraphuri == sQstr)
                                {
                                    // Force refreshGraph to believe that we
                                    // want a new graph.
                                    lastgraphuri = "";
                                    doPlotwithQuery();
                                }
                            }
                        };
                        autoreload_timer.schedule(reload_in * 1000);
                    }
                }
                if (--pending_requests > 0)
                {
                    pending_requests = 0;
                    doPlotwithQuery();
                }
            }
        });
    }
    
    private boolean addAllMetrics(final StringBuilder url)
    {
        int i = 0;
        boolean found_metric = false;
        for (final Widget widget : metrics)
        {
            if (!(widget instanceof MetricForm))
            {
                continue;
            }
            if( i++ > 0 )
            {
                url.append(" WITH ");
            }
            final MetricForm metric = (MetricForm) widget;
            found_metric |= metric.buildQueryString(url);
        }
        if (!found_metric)
        {
            graphstatus.setText("Please specify a metric.");
        }
        return found_metric;
    }

    private void asyncGetJson(final String url, final GotJsonCallback callback)
    {
        final RequestBuilder builder = new RequestBuilder(RequestBuilder.GET,
                url);
        try
        {
            builder.sendRequest(null, new RequestCallback()
            {
                public void onError(final Request request, final Throwable e)
                {
                    displayError("Failed to get " + url + ": " + e.getMessage());
                    // Since we don't call the callback we've been given, reset
                    // this
                    // bit of state as we're not going to retry anything right
                    // now.
                    pending_requests = 0;
                }

                public void onResponseReceived(final Request request,
                        final Response response)
                {
                    final int code = response.getStatusCode();
                    if (code == Response.SC_OK)
                    {
                        clearError();
                        callback.got(JSONParser.parse(response.getText()));
                        return;
                    }
                    else if (code >= Response.SC_BAD_REQUEST)
                    { // 400+ => Oops.
                        // Since we don't call the callback we've been given,
                        // reset this
                        // bit of state as we're not going to retry anything
                        // right now.
                        pending_requests = 0;
                        String err = response.getText();
                        // If the response looks like a JSON object, it probably
                        // contains
                        // an error message.
                        if (!err.isEmpty() && err.charAt(0) == '{')
                        {
                            final JSONValue json = JSONParser.parse(err);
                            final JSONObject result = json == null ? null
                                    : json.isObject();
                            final JSONValue jerr = result == null ? null
                                    : result.get("err");
                            final JSONString serr = jerr == null ? null : jerr
                                    .isString();
                            err = serr.stringValue();
                            // If the error message has multiple lines (which is
                            // common if
                            // it contains a stack trace), show only the first
                            // line and
                            // hide the rest in a panel users can expand.
                            final int newline = err.indexOf('\n', 1);
                            final String msg = "Request failed: "
                                    + response.getStatusText();
                            if (newline < 0)
                            {
                                displayError(msg + ": " + err);
                            }
                            else
                            {
                                displayError(msg);
                                final DisclosurePanel dp = new DisclosurePanel(
                                        err.substring(0, newline));
                                RootPanel.get("queryuimain").add(dp); // Attach
                                                                      // the
                                                                      // widget.
                                final InlineLabel content = new InlineLabel(err
                                        .substring(newline, err.length()));
                                content.addStyleName("fwf"); // For readable
                                                             // stack traces.
                                dp.setContent(content);
                                current_error.getElement().appendChild(
                                        dp.getElement());
                            }
                        }
                        else
                        {
                            displayError("Request failed while getting " + url
                                    + ": " + response.getStatusText());
                            // Since we don't call the callback we've been
                            // given, reset this
                            // bit of state as we're not going to retry anything
                            // right now.
                            pending_requests = 0;
                        }
                        graphstatus.setText("");
                    }
                }
            });
        }
        catch (RequestException e)
        {
            displayError("Failed to get " + url + ": " + e.getMessage());
        }
    }

    private void displayError(final String errmsg)
    {
        current_error.setText(errmsg);
        current_error.setVisible(true);
    }

    private void clearError()
    {
        current_error.setVisible(false);
    }

    static void setTextAlignCenter(final Element element)
    {
        element.getStyle().setProperty("textAlign", "center");
    }

    private final class AdjustYRangeCheckOnClick implements ClickHandler
    {

        private final CheckBox box;
        private final ValidatedTextBox range;

        public AdjustYRangeCheckOnClick(final CheckBox box,
                final ValidatedTextBox range)
        {
            this.box = box;
            this.range = range;
        }

        public void onClick(final ClickEvent event)
        {
            if (box.isEnabled() && box.getValue()
                    && "[0:]".equals(range.getValue()))
            {
                range.setValue("[1:]");
            }
            else if (box.isEnabled() && !box.getValue()
                    && "[1:]".equals(range.getValue()))
            {
                range.setValue("[0:]");
            }
        }

    };

    /**
     * Ensures all the widgets in the given panel have the same size. Otherwise
     * by default the panel will automatically resize itself to the contents of
     * the currently active panel's widget, which is annoying because it makes a
     * number of things move around in the UI.
     * 
     * @param panel
     *            The panel containing the widgets to resize.
     */
    private static void ensureSameWidgetSize(final DecoratedTabPanel panel)
    {
        if (!panel.isAttached())
        {
            throw new IllegalArgumentException("panel not attached: " + panel);
        }
        int maxw = 0;
        int maxh = 0;
        for (final Widget widget : panel)
        {
            final int w = widget.getOffsetWidth();
            final int h = widget.getOffsetHeight();
            if (w > maxw)
            {
                maxw = w;
            }
            if (h > maxh)
            {
                maxh = h;
            }
        }
        if (maxw == 0 || maxh == 0)
        {
            throw new IllegalArgumentException("maxw=" + maxw + " maxh=" + maxh);
        }
        for (final Widget widget : panel)
        {
            setOffsetWidth(widget, maxw);
            setOffsetHeight(widget, maxh);
        }
    }

    /**
     * Properly sets the total width of a widget. This takes into account
     * decorations such as border, margin, and padding.
     */
    private static void setOffsetWidth(final Widget widget, int width)
    {
        widget.setWidth(width + "px");
        final int offset = widget.getOffsetWidth();
        if (offset > 0)
        {
            width -= offset - width;
            if (width > 0)
            {
                widget.setWidth(width + "px");
            }
        }
    }

    /**
     * Properly sets the total height of a widget. This takes into account
     * decorations such as border, margin, and padding.
     */
    private static void setOffsetHeight(final Widget widget, int height)
    {
        widget.setHeight(height + "px");
        final int offset = widget.getOffsetHeight();
        if (offset > 0)
        {
            height -= offset - height;
            if (height > 0)
            {
                widget.setHeight(height + "px");
            }
        }
    }
    
    /**
     * Load Myview panel with Querylist
     */
    private void loadMyView( TreeItem aTItem )
    {
        int i = 0;
        ArrayList<String> sQueryList = null;
        final TreeItem sParentTreeItem;

        mMVFTable.removeAllRows();
        
        if (mMVrefreshTimer != null)
        {
            mMVrefreshTimer.cancel();
            mMVrefreshTimer = null;
        }
        
        if( aTItem.getText().compareTo("TmpView") == 0 )
        {
            sQueryList = mMVQuerylist;
        }
        else
        {
            if( ( sParentTreeItem = aTItem.getParentItem() ) != null )
            {
                sQueryList = getQueryListformHash( sParentTreeItem.getText() + "::" + aTItem.getText() );
            }
            else
            {
                sQueryList = getQueryListformHash( aTItem.getText() );
            }
        }

        if( sQueryList != null )
        {
            if( sQueryList.size() != 0 )
            {
                mMVFTable.setBorderWidth(1);
            }
            else
            {
                mMVFTable.setBorderWidth(0);
            }

            for( String sQuery : sQueryList )
            {
                printImageOnMyView( sQuery, i++ );
            }
            if( mMVrefreshCB.getValue() )
            {
                final int sTimeSec = Integer.parseInt(mMVrefreshTextB.getValue());
                if (sTimeSec >= 5)
                {
                    mMVrefreshTimer = new Timer()
                    {
                        public void run()
                        {
                            int j = 0;
                            if( mMVrefreshCB.getValue() )
                            {
                                loadMyView( mvTree.getSelectedItem() );
                            }
                        }
                    };
                    mMVrefreshTimer.schedule(sTimeSec * 1000);
                }
            } //if
        } //if
        else
        {
            mMVFTable.setBorderWidth(0);
        }
    }
    
    /**
     * Make graphs which saved in Plot tab
     */
    private void printImageOnMyView( final String aQuery, final int aInd )
    {
        final String sURL = "/q?";
        asyncGetJson(sURL + "GET json " + aQuery, new GotJsonCallback()
        {
            public void got(final JSONValue json)
            {
                final Image sImage = new Image();
                final JSONObject result = json.isObject();
                final JSONValue err = result.get("err");
                final Label resMsg = new Label();
                String msg = "";
                if (err != null)
                {
                    displayError("An error occurred while generating the graph: "
                            + err.isString().stringValue());
                    resMsg.setText("Please correct the error above.");
                }
                else
                {
                    clearError();
                    final JSONValue nplotted = result.get("plotted");
                    final JSONValue cachehit = result.get("cachehit");
                    if (cachehit != null)
                    {
                        msg += "Cache hit ("
                                + cachehit.isString().stringValue() + "). ";
                    }
                    if (nplotted != null
                            && nplotted.isNumber().doubleValue() > 0)
                    {
                        sImage.setUrl( sURL + "GET png " + aQuery );
                        sImage.setVisible(true);
                        msg += result.get("points").isNumber()
                                + " points retrieved, " + nplotted
                                + " points plotted";
                    }
                    else
                    {
                        sImage.setVisible(false);
                        msg += "Your query didn't return anything";
                    }
                    final JSONValue timing = result.get("timing");
                    if (timing != null)
                    {
                        msg += " in " + timing + "ms.";
                    }
                    else
                    {
                        msg += '.';
                    }
                }
                final JSONValue info = result.get("info");
                if (info != null)
                {
                    if (!msg.isEmpty())
                    {
                        msg += ' ';
                    }
                    msg += info.isString().stringValue();
                }
                resMsg.setText(msg);

                mMVFTable.setWidget( aInd/2 , aInd % 2, sImage);
            }
        });
    }
    
    public ArrayList<String> getQueryListformHash( String aKey )
    {
        return mMVQueryhash.get( aKey );
    }
    
    public ArrayList<String> setQueryListformHash( String aKey, ArrayList<String> aQueryList )
    {
        ArrayList<String> sDestList = new ArrayList<String>();
        Collections.copy(sDestList, aQueryList);
        return mMVQueryhash.put( aKey, sDestList );
    }
    
    public ArrayList<String> delQueryListformHash( String aKey )
    {
        return mMVQueryhash.remove( aKey );
    }
}
