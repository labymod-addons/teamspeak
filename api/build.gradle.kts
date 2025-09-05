import net.labymod.labygradle.common.extension.LabyModAnnotationProcessorExtension.ReferenceType

version = "0.1.0"

dependencies {
    labyProcessor();
    labyApi("api")
}

labyModAnnotationProcessor {
    referenceType = ReferenceType.INTERFACE
}