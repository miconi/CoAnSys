#!/bin/bash

#
# (C) 2010-2012 ICM UW. All rights reserved.
#

INSCRIPT_PATH=`echo -e "x=\"$0\"\nxl = x.rfind(\"/\")\ny=x[:xl]\nprint y" | python`
cd $INSCRIPT_PATH
eval "cd ../pig"

PREFIX="${12}/"
INFIX="${13}/"

if [ "$INFIX" = "/" -o "$INFIX" = "./" -o "$INFIX" = "." ]; then
	INFIX="`echo -e "import time\nprint time.time()" | python`/"
fi

DEF_SRC=${1}
DEF_LIM=${2}
DEF_FOLDS=${3}
dataNeigh="${PREFIX}${INFIX}${4}/"
dataForDocClassif="${PREFIX}${INFIX}${5}/"
dataEnriched="${PREFIX}${INFIX}${6}/"
dataModel="${PREFIX}${INFIX}${7}/"
dataTestRes="${PREFIX}${INFIX}${8}/"
categOccLimits=${9}
DEF_NEIGH_NUM=${10}
filterMethod=${11}




echo "~~~~~~~~~~~~~~~~~~~~~~~!!!!!!!!!!!!!!!!!!!!!!~~~~~~~~~~~~~~~~~~~~~~~~~"
<<PREDONE
PREDONE
# OveralTime = 01_createNeigh=2m37.015s + 02_assignFolds=5m20.634s + fold=5 * train/test=2 * 03_splitIntoTrain/Test=1m18.935s + 2*folds*fv*docsims*04_enrich + folds*fv*docsims*models*05_buildModel + folds*fv*docsims*models*06_testModel
# FOR fold=5, fv=simple_tfidf, docsims=cosine, models=mlknnThres OVERAL_TIME=x;
#create neighbours: about {finishTime}-{12:01:17} 3min on springer metadataonly
eval "hadoop dfs -rm -r -f ${dataNeigh}"
echo "pig -p DEF_SRC=${DEF_SRC} -p DEF_DST=${dataNeigh} -p DEF_FOLDS=${DEF_FOLDS} -p DEF_LIM=1 1_MODEL_CREATE_01_docs2neig.pig"
eval "time pig -p DEF_SRC=${DEF_SRC} -p DEF_DST=${dataNeigh} -p DEF_FOLDS=${DEF_FOLDS} -p DEF_LIM=1 1_MODEL_CREATE_01_docs2neig.pig"
echo "-------------------------------------------------------------------------"
echo "-------------------------------------------------------------------------"




eval "hadoop dfs -rm -r -f ${dataForDocClassif}"
echo "pig -p filterMethod=${filterMethod} -p dataNeigh=${dataNeigh} -p dataForDocClassif=${dataForDocClassif} -p DEF_LIM=${categOccLimits} -p DEF_FOLDS=${DEF_FOLDS} 1_MODEL_CREATE_02_assignAndCreateDocClassif.pig"
eval "time pig -p filterMethod=${filterMethod} -p dataNeigh=${dataNeigh} -p dataForDocClassif=${dataForDocClassif} -p DEF_LIM=${categOccLimits} -p DEF_FOLDS=${DEF_FOLDS} 1_MODEL_CREATE_02_assignAndCreateDocClassif.pig"
echo "-------------------------------------------------------------------------"
echo "-------------------------------------------------------------------------"



#using folds create ${fold} Train/Test Groups
for src in "${dataNeigh}" "${dataForDocClassif}"
do
	fold=${DEF_FOLDS}
	while [ ! ${fold} -eq 0 ];
		do
		let fold=fold-1
		#fold=0
		echo "-------------------------------------------------------------------------"
		echo "-------------------------------------------------------------------------"
		eval "hadoop dfs -rm -r -f ${src}_Tr_${fold}"
		eval "hadoop dfs -rm -r -f ${src}_Te_${fold}"
		echo "pig -p src=${src} -p fold=${fold} 1_MODEL_CREATE_03_split.pig"
		eval "time pig -p src=${src} -p fold=${fold} 1_MODEL_CREATE_03_split.pig"
	done
done


#calculate enriched docsim
for lev in "Tr" "Te"
do
	for sim in "cosine"
	do
		for fv in "tfidf" 
		do
			fold=${DEF_FOLDS}
			while [ ! ${fold} -eq 0 ];
			do
				let fold=fold-1
				#fold=0				
				inLocal="${dataNeigh}_${lev}_${fold}"
				outLocal="${dataEnriched}_${fv}_${sim}_${lev}_${fold}"
				echo "-------------------------------------------------------------------------"
				echo "-------------------------------------------------------------------------"
				eval "hadoop dfs -rm -r -f ${outLocal}"
				eval "hadoop dfs -rm -r -f ${outLocal}TMP"
				echo "pig -p neigh=${DEF_NEIGH_NUM} -p inLocal=${inLocal} -p dataForDocClassif=${dataForDocClassif} -p outLocal=${outLocal} -p featureVector=${fv} -p simmeth=${sim} 1_MODEL_CREATE_04_enrich.pig"
				eval "time pig -p neigh=${DEF_NEIGH_NUM} -p inLocal=${inLocal} -p dataForDocClassif=${dataForDocClassif} -p outLocal=${outLocal} -p featureVector=${fv} -p simmeth=${sim} 1_MODEL_CREATE_04_enrich.pig"
			done
		done
	done
done


#build model
for mb in "mlknnThres"
do
		for sim in "cosine"
		do
			for fv in "tfidf"
			do
				fold=${DEF_FOLDS}
				while [ ! ${fold} -eq 0 ];
				do
					let fold=fold-1
					#fold=0
					inLocal="${dataEnriched}_${fv}_${sim}_Tr_${fold}";
					outLocal="${dataModel}_${fv}_${sim}_${fold}_${mb}";
					modelBLD="${mb}Build"
					#eval "hadoop dfs -rm -r -f ${outLocal}TMP"
					#eval "hadoop dfs -rm -r -f ${outLocal}TMP2"
					echo "-------------------------------------------------------------------------"
					echo "-------------------------------------------------------------------------"
					eval "hadoop dfs -rm -r -f ${outLocal}"
					echo "pig  -p inLocal=${inLocal} -p outLocal=${outLocal} -p DEF_NEIGH=${DEF_NEIGH_NUM} -p MODEL_BLD_CLASS=${modelBLD} 1_MODEL_CREATE_05_build_model.pig"
					eval "time pig  -p inLocal=${inLocal} -p outLocal=${outLocal} -p DEF_NEIGH=${DEF_NEIGH_NUM} -p MODEL_BLD_CLASS=${modelBLD} 1_MODEL_CREATE_05_build_model.pig"
				done
			done
		done
done


#test model
for mb in "mlknnThres"
do
		for sim in "cosine"
		do
			for fv in "tfidf"
			do
				fold=${DEF_FOLDS}
				while [ ! ${fold} -eq 0 ];
				do
					let fold=fold-1
					#fold=0
					inMo="${dataModel}_${fv}_${sim}_${fold}_${mb}";
					inEn="${dataEnriched}_${fv}_${sim}_Te_${fold}";
					outLocal="${dataTestRes}_${fv}_${sim}_${fold}_${mb}";
					modelCLSF="${mb}Classify"
					echo "-------------------------------------------------------------------------"
					echo "-------------------------------------------------------------------------"
					eval "hadoop dfs -rm -r -f ${outLocal}"
					echo "pig  -p inEn=${inEn} -p inMo=${inMo} -p outLocal=${outLocal} -p MODEL_CLSF_CLASS=${modelCLSF}  1_MODEL_CREATE_06_test_model.pig"
					eval "time pig  -p inEn=${inEn} -p inMo=${inMo} -p outLocal=${outLocal} -p MODEL_CLSF_CLASS=${modelCLSF}  1_MODEL_CREATE_06_test_model.pig"
				done
			done
		done
done


#compare 
#eval "java Compare MB mlknnThres FV tfidf FOLDS 5 SIM cosine"

eval "rm -r -f /mnt/tmp/pdendek_results/"
eval "mkdir    /mnt/tmp/pdendek_results/"
for mb in "mlknnThres"
do
		for sim in "cosine"
		do
			for fv in "tfidf"
			do
				fold=${DEF_FOLDS}
				eval "echo fold is eq to ${fold}"
				fold=${DEF_FOLDS}
				while [ ! ${fold} -eq 0 ];
				do
					let fold=fold-1
					#fold=0
					outLocal="${dataTestRes}_${fv}_${sim}_${fold}_${mb}"
					#outLocal="${dataModel}_${fv}_${sim}_${fold}_${mb}";
					echo "-------------------------------------------------------------------------"
					echo "-------------------------------------------------------------------------"
					echo "hadoop dfs -get ${outLocal} /mnt/tmp/pdendek_results/"
					eval "hadoop dfs -get ${outLocal} /mnt/tmp/pdendek_results/" 
				done
			done
		done
done

eval "cd ../bash/"
eval "cp concatResults.sh /mnt/tmp/pdendek_results/concatResults.sh"
eval "cd /mnt/tmp/pdendek_results/"
eval "./concatResults.sh"
eval "cd ${HOME}"

<<NOT_YET
NOT_YET


<<DONE
DONE

