package pl.edu.icm.coansys.citations

import scala.collection.JavaConversions._
import com.nicta.scoobi.application.ScoobiApp
import com.nicta.scoobi.InputsOutputs._
import com.nicta.scoobi.Persist._
import pl.edu.icm.coansys.importers.models.DocumentProtosWrapper.DocumentWrapper
import pl.edu.icm.coansys.importers.models.DocumentProtos.DocumentMetadata
import pl.edu.icm.coansys.importers.models.PICProtos

/**
 * @author Mateusz Fedoryszak (m.fedoryszak@icm.edu.pl)
 */
object MockOutputCreator extends ScoobiApp {
  def run() {
    val in = args(0)
    val out = args(1)
    implicit val wrapperConverter = new BytesConverter[DocumentWrapper](_.toByteArray, DocumentWrapper.parseFrom(_))
    implicit val picOutConverter = new BytesConverter[PICProtos.PicOut](_.toByteArray, PICProtos.PicOut.parseFrom(_))
    val result = convertValueFromSequenceFile[DocumentWrapper](List(in))
      .map {
      wrapper =>
        val doc = DocumentMetadata.parseFrom(wrapper.getMproto)
        val outBuilder = PICProtos.PicOut.newBuilder()
        outBuilder.setDocId(doc.getExtId(0).getValue)
        for (ref <- doc.getReferenceList.toIterable) {
          outBuilder.addRefs(PICProtos.References.newBuilder().setDocId(ref.getExtId(0).getValue).setRefNum(ref.getBibRefPosition))
        }

        (doc.getKey, outBuilder.build())
    }
    persist(convertToSequenceFile(result, out))
  }
}
