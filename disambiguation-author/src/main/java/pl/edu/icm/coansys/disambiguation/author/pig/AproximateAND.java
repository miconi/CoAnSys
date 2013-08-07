package pl.edu.icm.coansys.disambiguation.author.pig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.pig.EvalFunc;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.data.DataBag;
import org.apache.pig.data.DefaultDataBag;
import org.apache.pig.data.DefaultTuple;
import org.apache.pig.data.Tuple;
import org.apache.pig.data.TupleFactory;
import org.slf4j.LoggerFactory;

import pl.edu.icm.coansys.commons.java.StackTraceExtractor;
import pl.edu.icm.coansys.disambiguation.author.features.disambiguators.DisambiguatorFactory;
import pl.edu.icm.coansys.disambiguation.features.Disambiguator;
import pl.edu.icm.coansys.disambiguation.features.FeatureInfo;

public class AproximateAND extends EvalFunc<DataBag> {

	private double threshold;
	private PigDisambiguator[] features;
	private List<FeatureInfo> featureInfos;
	private double sim[][];
	private Tuple datain[];
	private int N;
	//TODO: change lists to arrays where possible
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(AproximateAND.class);

	public AproximateAND(String threshold, String featureDescription){
		this.threshold = Double.parseDouble(threshold);
        this.featureInfos = FeatureInfo.parseFeatureInfoString(featureDescription);
        DisambiguatorFactory ff = new DisambiguatorFactory();

        int featureNum = 0;
        for ( FeatureInfo fi : featureInfos ){
        	if ( !fi.getDisambiguatorName().equals("") ) featureNum++;
        }

        this.features = new PigDisambiguator[featureNum];

        int index = -1;
        for ( FeatureInfo fi : featureInfos ){
        	if ( fi.getDisambiguatorName().equals("") ) continue;
        	index++;
        	Disambiguator d = ff.create(fi);
        	features[index] = new PigDisambiguator(d);
        }
	}

	
	/**
	 * @param Tuple (sname:chararray,{(contribId:chararray,contribPos:int,
	 * sname:chararray, metadata:map[{(chararray)}])},count:int)
	 * @see org.apache.pig.EvalFunc#exec(org.apache.pig.data.Tuple)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public DataBag exec( Tuple input ) throws IOException {

		if ( input == null || input.size() == 0 ) return null;
		try {
			//TODO optional:
			//it would be enough to take as argument only map bag with datagroup.
			//In that case this  function would be proof against table changes.
			//This change should be done during generating tables in pig script.

			DataBag contribs = (DataBag) input.get(0);  //taking bag with contribs

			if ( contribs == null || contribs.size() == 0 ) return null;

			Iterator<Tuple> it = contribs.iterator();
			N = (int) contribs.size();

			datain = new DefaultTuple[ N ];

			List< Map<String,Object> > contribsT = 
					new LinkedList< Map<String,Object> > ();

			int k = 0;
			//iterating through bag, dumping bug to Tuple array
			while ( it.hasNext() ) { 
				Tuple t = it.next();
				datain[ k++ ] = t;
				contribsT.add( (Map<String, Object>) t.get(3) ); //map with features
				//TODO: change map to list (disambiguators are created one by one 
				//as feature does, so both of them will be iterated in the same order).
			}

			//sim[][] init
			sim = new double[ N ][];
			for ( int i = 1; i < N; i++ ) {
				sim[i] = new double[i];

				for ( int j = 0; j < i; j++ ) {
					sim[i][j] = threshold;
                }
			}

			//sim[][] calculating
			calculateAffinityAndClustering( contribsT );

			//clusterList[ cluster_id ] = { contribs in cluster.. }
	        List < ArrayList<Integer> >  clusterList = splitIntoClusters();
	        
	        //bag: Tuple with (Object with (String (UUID), bag: { Tuple with ( String (contrib ID) ) } ) )
	        return createResultingTuples( clusterList );

		}catch(Exception e){
			// Throwing an exception would cause the task to fail.
			logger.error("Caught exception processing input row:\n" 
						+ StackTraceExtractor.getStackTrace(e));
				return null;
		}
	}

	//Find & Union
	//USE find() to call cluster id for each contributors from clusterAssicuiations tab!
	private int clusterAssociations[], clusterSize[];

	//finding cluster associations
	// o( log*( n ) ) ~ o( 1 )
	private int find( int a ) {
		if ( clusterAssociations[a] == a ) return a;
		int fa = find( clusterAssociations[a] );
		//Correcting cluster associations to the top one during traversing along the path.
		//Path is splited and straight connection with representative of union (cluster id) is made.
		clusterAssociations[a] = fa;
		return fa;
	}

	//cluster representatives union
	//o( 1 )
	private boolean union( int a, int b ) {
		int fa = find( a );
		int fb = find( b );

		if ( fa == fb ) return false;
		//choosing bigger cluster, union representatives in one cluster
		if (clusterSize[fa] <= clusterSize[fb]) {
			clusterSize[fb] += clusterSize[fa];
			clusterAssociations[fa] = fb;
		}
		else {
			clusterSize[fa] += clusterSize[fb];
			clusterAssociations[fb] = fa;
		}

		return true;
	}

	private void calculateAffinityAndClustering( List< Map<String,Object> > contribsT ) throws ExecException {
		//Find & Union init:		
		clusterAssociations = new int[N];
		clusterSize = new int[N];

		for ( int i = 0; i < N; i++ ) {
			clusterAssociations[i] = i;
			clusterSize[i] = 1;
		}

		//o( n^2 * features.length )
		//Skipping complexity of find because of its low complexity
		//The heuristic is that o( features.length ) would executed less frequently.
		for ( int i = 1; i < N; i++ ) {
			for ( int j = 0; j < i; j++ ) {

				//if i,j are already in one union, we say they are identical
				//and do not calculate precise similarity value
				if ( find( i ) == find( j ) ) {
					sim[i][j] = Double.POSITIVE_INFINITY;
					continue;
				}

				for ( int d = 0; d < features.length; d++ ) {

					FeatureInfo featureInfo = featureInfos.get(d);

					//the following gives sure, that disambiguator features[d]
					//does not exist
					if ( featureInfo.getDisambiguatorName().equals("") ) continue;

					//Taking features from each keys (name of extractor = feature name)
					//In contribsT.get(i) there is map we need.
					//From this map (collection of i'th contributor's features)
					//we take Bah with value of given feature.
					//Here we have sure that following Object = DateBag.
					Object oA = contribsT.get(i).get( featureInfo.getFeatureExtractorName() );
					Object oB = contribsT.get(j).get( featureInfo.getFeatureExtractorName() );

					double partial = features[d].calculateAffinity( oA, oB );
					partial = partial / featureInfo.getMaxValue() * featureInfo.getWeight();
					sim[i][j] += partial;

					//potentially the same contributors
        			if ( sim[i][j] >= 0 ) {
        				//so we union them in one cluster
        				union( i, j );
        				break;
        			}
				}
			}
		}
	}

	// o( N )
	protected List < ArrayList<Integer> > splitIntoClusters() {
		
		//TODO: change to arrrays, because we know clusters' sizes (clasterSize[])
		List < ArrayList<Integer> > clusters = new ArrayList < ArrayList< Integer > > ();
		// cluster[ id klastra ] =  array with contributors' simIds 


		for( int i = 0; i < N; i++ )
			clusters.add( new ArrayList<Integer> () );
		
        for ( int i = 0; i < N; i++ ) {
            clusters.get( find( i ) ).add( i );
        }

        //skipping empty clusters
        List < ArrayList<Integer> > ret = new ArrayList < ArrayList< Integer > > ();
		for( int i = 0; i < N; i++ ) {
			if ( !clusters.get( i ).isEmpty() ) {
				ret.add( clusters.get( i ) );
			}
		}
		return ret;
	}

	//o ( N * max_cluster_size )
	protected DataBag createResultingTuples( List < ArrayList<Integer> > clusters ) {
		//IdGenerator idgenerator = new UuIdGenerator();
    	DataBag ret = new DefaultDataBag();
    	int simIdToClusterId[] = new int[ sim.length ];

    	//iterating through clusters
    	for ( ArrayList<Integer> cluster: clusters ) {
        	DataBag contribDatas = new DefaultDataBag();
        	DataBag similarities = new DefaultDataBag();

        	//iterating through contribs (theirs simId) in cluster
        	for ( int i = 0; i < cluster.size(); i++ ) {

        		int sidX = cluster.get( i );

        		simIdToClusterId[ sidX ] = i;
        		contribDatas.add( datain[ sidX ] );

        		//adding precise calculated similarity values
        		//o( cluster_size )
        		for ( int j = 0; j < i; j++ ) {
        			int sidY = cluster.get( j );

        			if ( sidX <= sidY ||  simIdToClusterId[ sidX ] <= simIdToClusterId[ sidY ] ) {
        				String m = "Trying to write wrong data during create tuple: ";
        				m += ", sidX: " + sidX + ", sidY: " + sidY + ", simIdToClusterId[ sidX ]: " + simIdToClusterId[ sidX ] + ", simIdToClusterId[ sidY ]: " + simIdToClusterId[ sidY ];
        				throw new IllegalArgumentException( m );
        			}

        			if ( sim[ sidX ][ sidY ] != Double.NEGATIVE_INFINITY 
        					&& sim[ sidX ][ sidY ] != Double.POSITIVE_INFINITY ) {
        				Object[] clusterTriple = 
        						new Object[]{ simIdToClusterId[ sidX ], simIdToClusterId[ sidY ], sim[ sidX ][ sidY ] };
        				similarities.add( TupleFactory.getInstance().newTuple( 
        						Arrays.asList( clusterTriple ) ) );
        			}
        		}
        	}

        	Object[] to = new Object[]{ contribDatas, similarities };
	        ret.add(TupleFactory.getInstance().newTuple(Arrays.asList(to)));
        }

    	//bag with: { bag with date as in input but ordered by clusters, bag with triple similarities }
    
    	return ret;
	}
}
