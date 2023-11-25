package extrude

import ddf.minim.Minim
import ddf.minim.analysis.FFT
import ddf.minim.analysis.LanczosWindow
import org.openrndr.Fullscreen
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.extensions.Screenshots
import org.openrndr.extra.camera.Orbital
import org.openrndr.extra.imageFit.imageFit
import org.openrndr.extra.meshgenerators.boxMesh
import org.openrndr.extra.minim.MinimObject
import org.openrndr.ffmpeg.VideoPlayerFFMPEG
import org.openrndr.ffmpeg.loadVideoDevice
import org.openrndr.math.Vector3

// enable   "orx-mesh-generators" in build.gradle.kts
fun main() {
    application {
        configure {
            width = 700
            height = 700
            //fullscreen = Fullscreen.CURRENT_DISPLAY_MODE

        }
        program {
            val minim = Minim(MinimObject())
            val lineIn = minim.getLineIn(Minim.MONO, 2048, 48000f)
            val fft = FFT(lineIn.bufferSize(), lineIn.sampleRate())
            fft.window(LanczosWindow())
            ended.listen {
                minim.stop()
            }

            val cube = boxMesh()
            val rt = renderTarget(1280/4, 720/4) {
                colorBuffer()
                depthBuffer()
            }

            VideoPlayerFFMPEG.listDeviceNames().forEach {
                println(it)
            }
            val camera = loadVideoDevice(deviceName = "Integrated Webcam")
            camera.play()
            // val image = loadImage("data/images/8.png")
            extend(Orbital()) {
               // fov = 9.0
                far = 4000.0
                dampingFactor = 0.0
            }

            val bands = DoubleArray(256)

            extend {
                fft.forward(lineIn.mix)
                for (i in 0 until bands.size) {
                    bands[i] = fft.getBand(i).toDouble()
                }


                drawer.translate(0.0, 0.0, -120.0)
                drawer.rotate(Vector3.UNIT_Z, 180.0, TransformTarget.MODEL)
                drawer.rotate(Vector3.UNIT_X, 90.0, TransformTarget.MODEL)
                camera.draw(drawer, blind = true)
                drawer.isolatedWithTarget(rt) {
                    drawer.defaults()
                    drawer.ortho(rt)
                    drawer.clear(ColorRGBa.BLACK)
                    val lc = camera.colorBuffer
                    if (lc != null) {
                        drawer.imageFit(lc, drawer.bounds)
                    }
                    //drawer.circle(width/2.0, height/2.0, cos(seconds)*20.0+20.0)

                }
                drawer.shadeStyle = shadeStyle{

                    vertexTransform = """
                        int x = c_instance % p_width;
                        int y = c_instance / p_width;
                        vec2 uv = vec2(x/(p_width*1.0), y/(p_height*1.0));
                        x_position.x += (uv.x-0.5) * p_width;
                        x_position.z +=(uv.y-0.5) * p_height;
                        vec3 pixel = texture(p_image, uv).rgb;
                        float intensity = dot(pixel, vec3(1.0/3.0));
                        
                        // change this to make the extrusion weaker or stronger
                        x_position.y *= (1.0 + intensity * p_extrusion[int(intensity*128)] * 200.0  );
                    """.trimIndent()

                    fragmentTransform = """
                        int x = c_instance % p_width;
                        int y = c_instance / p_width;
                        vec2 uv = vec2(x/(p_width*1.0), y/(p_height*1.0));
                        vec3 pixel = texture(p_image, uv).rgb;
                        x_fill = vec4(pixel, 1.0);
                    """
                    parameter("image", rt.colorBuffer(0))
                    parameter("extrusion", bands)
                    parameter("width", rt.width)
                    parameter("height", rt.height)
                }

                drawer.vertexBufferInstances(listOf(cube), emptyList(), DrawPrimitive.TRIANGLES, instanceCount = rt.width * rt.height)
                drawer.defaults()
            }
        }
    }
}

//256