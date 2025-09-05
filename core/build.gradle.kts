import net.labymod.labygradle.common.extension.LabyModAnnotationProcessorExtension.ReferenceType

version = "0.1.0"

dependencies {
    labyProcessor();
    api(project(":api"))
}

labyModAnnotationProcessor {
    referenceType = ReferenceType.DEFAULT
}